package db

import (
	"errors"
	"fmt"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/gocql/gocql"
)

// NewSession Creates a new scylladb connection using env vars as connection settings
func NewSession() (*gocql.Session, error) {
	hosts := os.Getenv("SCYLLA_HOSTS") // e.g. "127.0.0.1"
	if hosts == "" {
		hosts = "127.0.0.1"
		fmt.Printf("Warning: SCYLLA_HOSTS environment variable not set, defaulting to %s\n", hosts)
	}
	keyspace := os.Getenv("SCYLLA_KEYSPACE") // e.g. "trove"
	if keyspace == "" {
		keyspace = "trove"
		fmt.Printf("Warning: SCYLLA_KEYSPACE environment variable not set, defaulting to %s\n", keyspace)
	}
	port, err := strconv.Atoi(os.Getenv("SCYLLA_PORT"))
	if err != nil || port <= 0 {
		port = 9042
		fmt.Printf("Warning: SCYLLA_PORT environment variable not set, defaulting to %s\n", port)
	}

	cluster := gocql.NewCluster(hosts)
	cluster.Keyspace = keyspace
	cluster.Consistency = gocql.Quorum
	cluster.Port = port
	return cluster.CreateSession()
}

func saveProto(session *gocql.Session, table string, whereClause string, args []interface{}, column string, message []byte, version string) error {
	queryStr := fmt.Sprintf("UPDATE %s SET %s = ?, schema_version = ? WHERE %s", table, column, whereClause)
	allArgs := append([]interface{}{message, version}, args...)
	return session.Query(queryStr, allArgs...).Exec()
}

func loadProto(session *gocql.Session, table string, whereClause string, args []interface{}, column string) ([]byte, string, error) {
	queryStr := fmt.Sprintf("SELECT %s, schema_version FROM %s WHERE %s LIMIT 1", column, table, whereClause)
	var data []byte
	var version string
	if err := session.Query(queryStr, args...).Scan(&data, &version); err != nil {
		return nil, "", err
	}
	return data, version, nil
}

// === PLAYER-LEVEL MAPPERS ===

var identifierPattern = regexp.MustCompile(`^[a-zA-Z_][a-zA-Z0-9_]*$`)

func isSafeIdentifier(s string) bool {
	return identifierPattern.MatchString(s)
}

func SaveData(session *gocql.Session, table string, superkeys map[string]string, data map[string][]byte, version string) error {
	if !isSafeIdentifier(table) {
		return fmt.Errorf("invalid table name: %s", table)
	}

	if len(superkeys) == 0 {
		return errors.New("must specify at least one superkey")
	}

	if len(data) == 0 {
		return errors.New("must specify at least one column")
	}

	whereKeys := make([]string, 0, len(superkeys))
	whereVals := make([]interface{}, 0, len(superkeys))
	for key, val := range superkeys {
		if !isSafeIdentifier(key) {
			return fmt.Errorf("invalid key in superkeys: %s", key)
		}
		whereKeys = append(whereKeys, key+" = ?")
		whereVals = append(whereVals, val)
	}
	whereClause := strings.Join(whereKeys, " AND ")

	setKeys := make([]string, 0, len(data))
	setVals := make([]interface{}, 0, len(data))
	for key, val := range data {
		if !isSafeIdentifier(key) {
			return fmt.Errorf("invalid column name: %s", key)
		}
		setKeys = append(setKeys, key+" = ?")
		setVals = append(setVals, val)
	}
	setClause := strings.Join(setKeys, ", ")

	queryStr := fmt.Sprintf("UPDATE %s SET %s, schema_version = ? WHERE %s", table, setClause, whereClause)

	allArgs := append(setVals, version)
	allArgs = append(allArgs, whereVals...)

	return session.Query(queryStr, allArgs...).Exec()
}

func LoadData(session *gocql.Session, table string, superkeys map[string]string, columns []string) (map[string][]byte, string, error) {
	if !isSafeIdentifier(table) {
		return nil, "", fmt.Errorf("invalid table name: %s", table)
	}

	if len(superkeys) == 0 {
		return nil, "", errors.New("must specify at least one superkey")
	}

	if len(columns) == 0 {
		return nil, "", errors.New("must specify at least one column to select")
	}

	for _, col := range columns {
		if !isSafeIdentifier(col) {
			return nil, "", fmt.Errorf("invalid column name: %s", col)
		}
	}

	whereKeys := make([]string, 0, len(superkeys))
	whereVals := make([]interface{}, 0, len(superkeys))
	for key, val := range superkeys {
		if !isSafeIdentifier(key) {
			return nil, "", fmt.Errorf("invalid superkey name: %s", key)
		}
		whereKeys = append(whereKeys, key+" = ?")
		whereVals = append(whereVals, val)
	}
	whereClause := strings.Join(whereKeys, " AND ")

	selectClause := strings.Join(columns, ", ") + ", schema_version"
	queryStr := fmt.Sprintf("SELECT %s FROM %s WHERE %s LIMIT 1", selectClause, table, whereClause)

	// Prepare holders for values
	values := make([]interface{}, len(columns))
	columnData := make(map[string][]byte, len(columns))

	for i := range columns {
		var b []byte
		values[i] = &b
		columnData[columns[i]] = b
	}

	var schemaVersion string
	values = append(values, &schemaVersion)

	if err := session.Query(queryStr, whereVals...).Scan(values...); err != nil {
		return nil, "", err
	}

	for i, col := range columns {
		columnData[col] = *(values[i].(*[]byte))
	}

	return columnData, schemaVersion, nil
}

// === LOCK MANAGEMENT ===

// ClaimLock tries to INSERT or RENEW a lock for the given player.
// Returns (acquiredOrRenewed, expiresAt, error).
func ClaimLock(session *gocql.Session, userID, serverID string, leaseMillis int64) (bool, time.Time, error) {
	now := time.Now()
	expires := now.Add(time.Duration(leaseMillis) * time.Millisecond)

	// Try ACQUIRE
	const acquireCQL = `
		INSERT INTO user_locks
			(user_id, server_id, last_renewed, expires_at)
	  		VALUES (?, ?, ?, ?)
	  	IF NOT EXISTS;`
	applied, err := session.Query(acquireCQL, userID, serverID, now, expires).MapScanCAS(make(map[string]interface{}))
	if err != nil {
		return false, time.Time{}, errors.Join(err, errors.New("failed to acquire lock"))
	}
	if applied {
		expires := time.Now().Add(time.Duration(leaseMillis) * time.Millisecond)
		return true, expires, nil
	}

	// Try RENEW
	const renewCQL = `
	  	UPDATE user_locks SET last_renewed = ?, expires_at  = ?
	  	WHERE user_id = ? IF server_id = ?;`
	applied, err = session.Query(renewCQL, now, expires, userID, serverID).MapScanCAS(make(map[string]interface{}))

	if err != nil {
		return false, time.Time{}, errors.Join(err, errors.New("failed to renew lock"))
	}
	if applied {
		expires := time.Now().Add(time.Duration(leaseMillis) * time.Millisecond)
		return true, expires, nil
	}

	// not applied → someone else holds the lock
	return false, time.Time{}, nil
}

// ReleaseLock deletes the lock if owned by serverID.
func ReleaseLock(session *gocql.Session, userID, serverID string) (bool, error) {
	deleteCQL := `
		DELETE FROM user_locks
		WHERE user_id = ?
		IF server_id = ?;
	`
	applied, err := session.Query(deleteCQL, userID, serverID).MapScanCAS(make(map[string]interface{}))
	return applied, err
}

// GetLockStatus returns (locked, ownerServerID, expiresAt, error).
func GetLockStatus(session *gocql.Session, userID gocql.UUID) (bool, string, time.Time, error) {
	var sid string
	var expiresAt time.Time
	statusCQL := `SELECT server_id, expires_at FROM user_locks WHERE user_id = ? LIMIT 1`
	err := session.Query(statusCQL, userID).Scan(&sid, &expiresAt)
	if err != nil {
		if err == gocql.ErrNotFound {
			return false, "", time.Time{}, nil
		}
		return false, "", time.Time{}, err
	}

	if time.Now().After(expiresAt) {
		// stale: let client delete or let service clean up
		return false, "", time.Time{}, nil
	}

	return true, sid, expiresAt, nil
}

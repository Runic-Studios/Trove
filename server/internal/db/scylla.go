package db

import (
	"errors"
	"fmt"
	"log"
	"os"
	"regexp"
	"runtime/debug"
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

	err := session.Query(queryStr, allArgs...).Exec()

	if err != nil {
		log.Printf("db internal error saving when executing %s\n%v\n%s", queryStr, err, debug.Stack())
	}
	return err
}

type Row struct {
	Data          map[string][]byte
	SchemaVersion string
}

func LoadData(session *gocql.Session, table string, superkeys map[string]string, columns []string) ([]Row, error) {
	if !isSafeIdentifier(table) {
		return nil, fmt.Errorf("invalid table name: %s", table)
	}

	if len(superkeys) == 0 {
		return nil, errors.New("must specify at least one superkey")
	}

	if len(columns) == 0 {
		return nil, errors.New("must specify at least one column to select")
	}

	for _, col := range columns {
		if !isSafeIdentifier(col) {
			return nil, fmt.Errorf("invalid column name: %s", col)
		}
	}

	whereKeys := make([]string, 0, len(superkeys))
	whereVals := make([]interface{}, 0, len(superkeys))
	for key, val := range superkeys {
		if !isSafeIdentifier(key) {
			return nil, fmt.Errorf("invalid superkey name: %s", key)
		}
		whereKeys = append(whereKeys, key+" = ?")
		whereVals = append(whereVals, val)
	}
	whereClause := strings.Join(whereKeys, " AND ")

	selectClause := strings.Join(columns, ", ") + ", schema_version"
	queryStr := fmt.Sprintf("SELECT %s FROM %s WHERE %s", selectClause, table, whereClause)

	iter := session.Query(queryStr, whereVals...).Iter()

	var results []Row
	for {
		// allocate holders
		holders := make([]interface{}, len(columns)+1)
		for i := range columns {
			holders[i] = new(*interface{})
		}
		holders[len(columns)] = new(string)

		// try to scan one row
		if !iter.Scan(holders...) {
			break
		}

		// build Row from holders
		data := make(map[string][]byte, len(columns))
		for i, col := range columns {
			raw := *(holders[i].(*interface{}))
			data[col] = toByteArray(raw)
		}
		version := *holders[len(columns)].(*string)

		results = append(results, Row{Data: data, SchemaVersion: version})
	}

	if err := iter.Close(); err != nil {
		log.Printf("db internal error loading when executing %s\n%v\n%s", queryStr, err, debug.Stack())
		return nil, err
	}

	return results, nil
}

func toByteArray(val interface{}) []byte {
	switch v := val.(type) {
	case []byte:
		return v
	case string:
		return []byte(v)
	case int:
		return []byte(strconv.Itoa(v))
	case int64:
		return []byte(strconv.FormatInt(v, 10))
	case float64:
		return []byte(strconv.FormatFloat(v, 'f', -1, 64))
	case bool:
		return []byte(strconv.FormatBool(v))
	default:
		return []byte(fmt.Sprintf("%v", v))
	}
}

// === LOCK MANAGEMENT ===

// ClaimLock tries to INSERT, RENEW, or TAKEOVER a lock for the given player.
// Returns (acquiredOrRenewed, expiresAt, error).
func ClaimLock(session *gocql.Session, userID, serverID string, leaseMillis int64) (bool, time.Time, error) {
	now := time.Now()
	expires := now.Add(time.Duration(leaseMillis) * time.Millisecond)

	// 1) ACQUIRE: insert if no lock exists yet
	const acquireCQL = `
        INSERT INTO user_locks (user_id, server_id, last_renewed, expires_at)
        VALUES (?, ?, ?, ?)
        IF NOT EXISTS;`
	applied, err := session.Query(acquireCQL, userID, serverID, now, expires).
		MapScanCAS(make(map[string]interface{}))
	if err != nil {
		return false, time.Time{}, fmt.Errorf("failed to acquire lock: %w", err)
	}
	if applied {
		return true, expires, nil
	}

	// 2) RENEW: update only if the current lock belongs to this server
	const renewCQL = `
        UPDATE user_locks
        SET last_renewed = ?, expires_at = ?
        WHERE user_id = ?
        IF server_id = ?;`
	applied, err = session.Query(renewCQL, now, expires, userID, serverID).
		MapScanCAS(make(map[string]interface{}))
	if err != nil {
		return false, time.Time{}, fmt.Errorf("failed to renew lock: %w", err)
	}
	if applied {
		return true, expires, nil
	}

	// 3) TAKEOVER: overwrite only if the existing lock has already expired
	const takeoverCQL = `
        UPDATE user_locks
        SET server_id = ?, last_renewed = ?, expires_at = ?
        WHERE user_id = ?
        IF expires_at < ?;`
	applied, err = session.Query(takeoverCQL, serverID, now, expires, userID, now).
		MapScanCAS(make(map[string]interface{}))
	if err != nil {
		return false, time.Time{}, fmt.Errorf("failed to takeover expired lock: %w", err)
	}
	if applied {
		return true, expires, nil
	}

	// 4) not applied -> another server holds an unexpired lock
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

// Exists returns true if table contains at least one row where
// each key in superKeys equals its corresponding value
func Exists(session *gocql.Session, table string, superKeys map[string]string) (bool, error) {
	var preds []string
	var args []interface{}
	for col, val := range superKeys {
		preds = append(preds, fmt.Sprintf("%s = ?", col))
		args = append(args, val)
	}
	where := strings.Join(preds, " AND ")

	// Query for any matching row
	cql := fmt.Sprintf(
		"SELECT * FROM %s WHERE %s LIMIT 1;",
		table, where,
	)
	iter := session.Query(cql, args...).Iter()

	// Try to map one row into a dummy map
	if iter.MapScan(make(map[string]interface{})) {
		return true, nil
	}

	if err := iter.Close(); err != nil {
		return false, err
	}
	return false, nil
}

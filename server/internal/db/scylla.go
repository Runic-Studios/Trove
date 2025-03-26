package db

import (
	"fmt"
	"os"

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

	cluster := gocql.NewCluster(hosts)
	cluster.Keyspace = keyspace
	cluster.Consistency = gocql.Quorum
	return cluster.CreateSession()
}

// SaveRecord inserts or updates to db
func SaveRecord(session *gocql.Session, databaseName, recordID string, version int, data []byte) error {
	// e.g. table: trove.players_records, trove.guilds_records, etc.
	table := fmt.Sprintf("%s_records", databaseName)

	queryStr := fmt.Sprintf("INSERT INTO %s (record_id, version, data) VALUES (?, ?, ?)", table)
	return session.Query(queryStr, recordID, version, data).Exec()
}

// Record for reading
type Record struct {
	RecordID string
	Version  int
	Data     []byte
}

// LoadRecord retrieve an entry from db
func LoadRecord(session *gocql.Session, databaseName, recordID string) (*Record, error) {
	table := fmt.Sprintf("%s_records", databaseName)
	queryStr := fmt.Sprintf("SELECT version, data FROM %s WHERE record_id = ? LIMIT 1", table)

	var rec Record
	rec.RecordID = recordID
	if err := session.Query(queryStr, recordID).Scan(&rec.Version, &rec.Data); err != nil {
		return nil, err
	}
	return &rec, nil
}

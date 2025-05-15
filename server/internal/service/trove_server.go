package service

import (
	"context"
	"errors"
	"fmt"
	"github.com/Runic-Studios/Trove/server/internal/db"
	"sync"
	"time"

	"github.com/Runic-Studios/Trove/server/gen/api/trove"
	"github.com/gocql/gocql"
)

// lockEntry lives in memory for quick guard checks
type lockEntry struct {
	serverID  string
	expiresAt time.Time
}

// TroveServer implements SaveColumn & LoadColumn, holds in-memory set of locks
type TroveServer struct {
	session      *gocql.Session
	transformers *TransformerChain
	locks        sync.Map
	trove.UnimplementedTroveServiceServer
}

// NewTroveServer wires up the Scylla session and a map of chains keyed by "table.column"
func NewTroveServer(
	session *gocql.Session,
	transformers *TransformerChain,
) *TroveServer {
	s := &TroveServer{session: session, transformers: transformers}
	go s.evictExpiredLocks()
	return s
}

// evictExpiredLocks runs every minute to purge stale entries.
func (s *TroveServer) evictExpiredLocks() {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()

	for now := range ticker.C {
		s.locks.Range(func(key, value interface{}) bool {
			entry := value.(lockEntry)
			if now.After(entry.expiresAt) {
				s.locks.Delete(key)
			}
			return true
		})
	}
}

// ClaimLock will try to acquire or renew a lease for this player.
func (s *TroveServer) ClaimLock(
	_ context.Context,
	req *trove.ClaimLockRequest,
) (*trove.ClaimLockResponse, error) {
	userId := req.GetUserId()
	sid := req.GetServerId()
	leaseMillis := req.GetLeaseMillis()
	if userId == "" || sid == "" || leaseMillis <= 0 {
		return &trove.ClaimLockResponse{
			Success:      false,
			ErrorMessage: "user_id, server_id and lease_millis are required",
		}, nil
	}

	// Try to acquire or renew
	acquiredOrRenewed, _, err := db.ClaimLock(s.session, userId, sid, leaseMillis)
	if err != nil {
		return &trove.ClaimLockResponse{Success: false, ErrorMessage: err.Error()}, nil
	} else if acquiredOrRenewed {
		expires := time.Now().Add(time.Duration(leaseMillis) * time.Millisecond)
		s.locks.Store(userId, lockEntry{serverID: sid, expiresAt: expires})
		return &trove.ClaimLockResponse{
			Success:             true,
			ExpiresAtUnixMillis: expires.UnixMilli(),
		}, nil
	}

	// failed both acquire and renew -> someone else holds it
	return &trove.ClaimLockResponse{
		Success:      false,
		ErrorMessage: "lock is held by another server",
	}, nil
}

// ReleaseLock drops the lease if we still own it.
func (s *TroveServer) ReleaseLock(
	_ context.Context,
	req *trove.ReleaseLockRequest,
) (*trove.ReleaseLockResponse, error) {
	userId := req.GetUserId()
	sid := req.GetServerId()
	if userId == "" || sid == "" {
		return &trove.ReleaseLockResponse{
			Success:      false,
			ErrorMessage: "user_id and server_id are required",
		}, nil
	}

	applied, err := db.ReleaseLock(s.session, userId, sid)
	if err != nil {
		return &trove.ReleaseLockResponse{Success: false, ErrorMessage: err.Error()}, nil
	} else if applied {
		s.locks.Delete(userId)
		return &trove.ReleaseLockResponse{Success: true}, nil
	}

	return &trove.ReleaseLockResponse{
		Success:      false,
		ErrorMessage: "cannot release: lock not held by you",
	}, nil
}

// validateLock checks our in‑memory map for an unexpired, matching lease
func (s *TroveServer) validateLock(userID, serverID string) error {
	v, ok := s.locks.Load(userID)
	if !ok {
		return errors.New("no lock held for this player")
	}
	entry := v.(lockEntry)
	if entry.serverID != serverID {
		return errors.New("lock is owned by a different server")
	}
	if time.Now().After(entry.expiresAt) {
		s.locks.Delete(userID)
		return errors.New("lock has expired")
	}
	return nil
}

// Save writes multiple columns
func (s *TroveServer) Save(
	_ context.Context,
	req *trove.SaveRequest,
) (*trove.SaveResponse, error) {
	// enforce lock
	if req.GetLock() == nil {
		return &trove.SaveResponse{Success: false, ErrorMessage: "lock info missing"}, nil
	}
	if err := s.validateLock(
		req.GetLock().GetUserId(),
		req.GetLock().GetServerId(),
	); err != nil {
		return &trove.SaveResponse{Success: false, ErrorMessage: err.Error()}, nil
	}

	table := req.GetTable()
	superKeys := req.GetSuperKeys()
	data := req.GetColumnData()

	// validate
	if table == "" || superKeys == nil || data == nil {
		return &trove.SaveResponse{
			Success:      false,
			ErrorMessage: "missing required fields",
		}, nil
	}

	// update
	latest := s.transformers.LatestVersion

	err := db.SaveData(s.session, table, superKeys, data, latest)
	if err != nil {
		return &trove.SaveResponse{
			Success:      false,
			ErrorMessage: fmt.Sprintf("error saving data: %+v", err),
		}, nil
	}

	return &trove.SaveResponse{Success: true}, nil
}

// Load reads one column, runs TransformUp(table, column, ...),
// resaves if upgraded, and returns version + blob.
func (s *TroveServer) Load(
	_ context.Context,
	req *trove.LoadRequest,
) (*trove.LoadResponse, error) {
	// enforce lock
	if req.GetLock() == nil {
		return &trove.LoadResponse{Success: false, ErrorMessage: "lock info missing"}, nil
	}
	if err := s.validateLock(
		req.GetLock().GetUserId(),
		req.GetLock().GetServerId(),
	); err != nil {
		return &trove.LoadResponse{Success: false, ErrorMessage: err.Error()}, nil
	}

	table := req.GetTable()
	superKeys := req.GetSuperKeys()
	columns := req.GetColumns()

	// validate
	if table == "" || superKeys == nil || columns == nil {
		return &trove.LoadResponse{
			Success:      false,
			ErrorMessage: "missing required fields",
		}, nil
	}

	data, version, err := db.LoadData(s.session, table, superKeys, columns)
	if err != nil {
		return &trove.LoadResponse{
			Success:      false,
			ErrorMessage: fmt.Sprintf("error loading data: %+v", err),
		}, nil
	}

	// transform if we have a chain for this table.column
	latest := s.transformers.LatestVersion
	if version != latest {
		up := make(map[string][]byte, len(data))
		for column, datum := range data {
			dataUp, err := s.transformers.TransformUp(table, column, version, datum)
			if err != nil {
				return &trove.LoadResponse{
					Success:      false,
					ErrorMessage: fmt.Sprintf("failed to transform column %s: %+v", column, err),
				}, nil
			}
			up[column] = dataUp
		}
		version = latest

		// trigger a save
		err := db.SaveData(s.session, table, superKeys, up, version)
		if err != nil {
			return &trove.LoadResponse{
				Success:      false,
				ErrorMessage: fmt.Sprintf("failed to save transformed data: %+v", err),
			}, nil
		}
		data = up
	}

	return &trove.LoadResponse{
		Success:    true,
		ColumnData: data,
	}, nil
}

// Exists checks if a row exists in a given table that contains the requested super keys
// Used when a user first logs in to check if they have data already, or if we need to populate it
func (s *TroveServer) Exists(
	_ context.Context,
	req *trove.ExistsRequest,
) (*trove.ExistsResponse, error) {
	// enforce lock
	if req.GetLock() == nil {
		return &trove.ExistsResponse{Success: false, ErrorMessage: "lock info missing"}, nil
	}
	if err := s.validateLock(
		req.GetLock().GetUserId(),
		req.GetLock().GetServerId(),
	); err != nil {
		return &trove.ExistsResponse{Success: false, ErrorMessage: err.Error()}, nil
	}

	table := req.GetTable()
	superKeys := req.GetSuperKeys()

	exists, err := db.Exists(s.session, table, superKeys)
	if err != nil {
		return &trove.ExistsResponse{
			Success:      false,
			ErrorMessage: fmt.Sprintf("failed to check if row exists: %+v", err),
		}, nil
	}

	return &trove.ExistsResponse{
		Success: true,
		Exists:  exists,
	}, nil
}

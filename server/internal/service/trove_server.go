package service

import (
	"context"
	"fmt"

	"github.com/Runic-Studios/Trove/server/gen/api/trove"
	"github.com/Runic-Studios/Trove/server/internal/db"
	"github.com/gocql/gocql"
)

// TroveServer implementation of the gRPC server that will handle requests to trove
type TroveServer struct {
	session      *gocql.Session
	transformers map[string]*TransformerChain
	trove.UnimplementedTroveServiceServer
}

// NewTroveServer creates a new trove server implementation with a given set of database transformers
func NewTroveServer(s *gocql.Session, t map[string]*TransformerChain) *TroveServer {
	return &TroveServer{session: s, transformers: t}
}

// SaveData writes the data to db, does not check for schema version compatibility.
// This means you CAN write bad data! Onus is on you to match the schema.
func (t *TroveServer) SaveData(_ context.Context, req *trove.SaveDataRequest) (*trove.SaveDataResponse, error) {
	if req.GetDatabaseName() == "" || req.GetRecordId() == "" || len(req.GetDataBlob()) == 0 {
		return &trove.SaveDataResponse{
			Success:      false,
			ErrorMessage: "missing required fields",
		}, nil
	}

	err := db.SaveRecord(t.session, req.GetDatabaseName(), req.GetRecordId(), req.GetSchemaVersion(), req.GetDataBlob())
	if err != nil {
		return &trove.SaveDataResponse{
			Success:      false,
			ErrorMessage: fmt.Sprintf("failed to save record: %v", err),
		}, nil
	}

	return &trove.SaveDataResponse{Success: true}, nil
}

// LoadData reads from the db, transforming schemas upwards if needed
// Automatically saves to the db is a transform was needed to ensure it reflects its read state
func (t *TroveServer) LoadData(_ context.Context, req *trove.LoadDataRequest) (*trove.LoadDataResponse, error) {
	if req.GetDatabaseName() == "" || req.GetRecordId() == "" {
		return &trove.LoadDataResponse{
			Success:      false,
			ErrorMessage: "missing required fields",
		}, nil
	}

	rec, err := db.LoadRecord(t.session, req.GetDatabaseName(), req.GetRecordId())
	if err != nil {
		return &trove.LoadDataResponse{
			Success:      false,
			ErrorMessage: fmt.Sprintf("failed to load record: %v", err),
		}, nil
	}

	data := rec.Data
	transformer, ok := t.transformers[req.GetDatabaseName()]
	if ok {
		var err error
		data, err = transformer.TransformUp(rec.Version, data)
		if err != nil {
			return &trove.LoadDataResponse{
				Success:      false,
				ErrorMessage: fmt.Sprintf("transform error: %v", err),
			}, nil
		}
	} else {
		fmt.Printf("Warning: didn't find any valid transformer for database %s, forwarding data instead\n", req.GetDatabaseName())
	}

	// This implies that we have upgraded the data: we should save first to be safe
	if rec.Version < transformer.LatestVersion {
		if err := db.SaveRecord(t.session, req.GetDatabaseName(), req.GetRecordId(), transformer.LatestVersion, data); err != nil {
			return &trove.LoadDataResponse{
				Success:      false,
				ErrorMessage: fmt.Sprintf("failed to re-save upgraded data: %v", err),
			}, nil
		}
	}

	// return the latest data
	return &trove.LoadDataResponse{
		Success:  true,
		DataBlob: data,
	}, nil
}

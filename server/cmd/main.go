package main

import (
	"fmt"
	"github.com/Runic-Studios/Trove/server/internal/transformers"
	"log"
	"net"
	"os"

	"github.com/Runic-Studios/Trove/server/gen/api/trove"
	"github.com/Runic-Studios/Trove/server/internal/db"
	"github.com/Runic-Studios/Trove/server/internal/service"
	"google.golang.org/grpc"
)

func main() {
	sess, err := db.NewSession()
	if err != nil {
		log.Fatalf("failed to create scylla session: %v", err)
	}
	defer sess.Close()

	port := os.Getenv("TROVE_SERVER_PORT")
	if port == "" {
		port = "9090"
		fmt.Printf("Warning: TROVE_SERVER_PORT environment variable, not set, defaulting to %s\n", port)
	}

	lis, err := net.Listen("tcp", ":"+port)
	if err != nil {
		log.Fatalf("failed to listen on :%s, %v", port, err)
	}

	grpcServer := grpc.NewServer()
	srv := service.NewTroveServer(sess, transformers.Transformers)
	trove.RegisterTroveServiceServer(grpcServer, srv)

	fmt.Printf("Trove-Server listening on :%s\n", port)
	if err := grpcServer.Serve(lis); err != nil {
		log.Fatalf("failed to serve gRPC: %v", err)
	}
}

module github.com/Runic-Studios/Trove/server

go 1.24.2

replace github.com/Runic-Studios/Trove/api => ../api

require (
	github.com/gocql/gocql v1.7.0
	google.golang.org/grpc v1.71.1
	google.golang.org/protobuf v1.36.6
)

require (
	github.com/golang/snappy v1.0.0 // indirect
	github.com/hailocab/go-hostpool v0.0.0-20160125115350-e80d13ce29ed // indirect
	golang.org/x/net v0.39.0 // indirect
	golang.org/x/sys v0.32.0 // indirect
	golang.org/x/text v0.24.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20250414145226-207652e42e2e // indirect
	gopkg.in/inf.v0 v0.9.1 // indirect
)

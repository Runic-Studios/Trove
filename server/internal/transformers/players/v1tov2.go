package players

import (
	"fmt"
	v1 "github.com/Runic-Studios/Trove/server/gen/api/db_schema/players/v1"
	v2 "github.com/Runic-Studios/Trove/server/gen/api/db_schema/players/v2"
	"google.golang.org/protobuf/proto"
	"strconv"
	"strings"
)

// V1ToV2 transforms a serialized v1.PlayerData into v2.PlayerData bytes.
// Example transformer
func V1ToV2(data []byte) ([]byte, error) {
	var oldData v1.PlayerData
	if err := proto.Unmarshal(data, &oldData); err != nil {
		return nil, fmt.Errorf("failed to unmarshal v1 data: %w", err)
	}

	// parse the old location string
	// e.g. "x=100,y=64"
	locParts := strings.Split(oldData.Location, ",")
	var xVal, yVal float64
	for _, part := range locParts {
		kv := strings.Split(part, "=")
		if len(kv) != 2 {
			continue
		}
		key, valStr := kv[0], kv[1]
		fVal, err := strconv.ParseFloat(valStr, 32)
		if err != nil {
			continue
		}
		if key == "x" {
			xVal = fVal
		} else if key == "y" {
			yVal = fVal
		}
	}

	newData := v2.PlayerData{
		PlayerId: oldData.PlayerId,
		Level:    oldData.Level,
		Location: &v2.PlayerData_Location{
			X: float32(xVal),
			Y: float32(yVal),
		},
		Title: "Novice", // default value for new field
	}

	return proto.Marshal(&newData)
}

package transformers

import (
	"github.com/Runic-Studios/Trove/server/internal/service"
)

// V1Transformer transformer specifically for transforming data in the players database
var V1Transformer = &service.TransformerChain{
	LatestVersion: "v1",
	// Example transformer
	Links: map[service.VersionPair]service.TransformerFunc{},
}

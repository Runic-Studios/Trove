package transformers

import (
	"github.com/Runic-Studios/Trove/server/internal/service"
	"github.com/Runic-Studios/Trove/server/internal/transformers/players"
)

// PlayersTransformer transformer specifically for transforming data in the players database
var PlayersTransformer = &service.TransformerChain{
	LatestVersion: "v3",
	// Example transformer
	Links: map[service.VersionPair]service.TransformerFunc{
		{"v1", "v2"}: players.V1ToV2,
	},
}

// Transformers maps the db name -> transformer chain
var Transformers = map[string]*service.TransformerChain{
	"players": PlayersTransformer,
}

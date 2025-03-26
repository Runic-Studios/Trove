package service

import (
	"fmt"
)

// TransformerFunc transforms data from one version to the next
type TransformerFunc func([]byte) ([]byte, error)

// VersionPair identifies the path from currentVersion -> nextVersion
type VersionPair struct {
	From int
	To   int
}

// TransformerChain Represents a transformer that can perform linked transformation operations
type TransformerChain struct {
	LatestVersion int
	Links         map[VersionPair]TransformerFunc
}

// TransformUp automatically migrates data from `fromVer` to LATEST_VERSION
func (transformer *TransformerChain) TransformUp(fromVer int, data []byte) ([]byte, error) {
	current := fromVer
	out := data

	for current < transformer.LatestVersion {
		next := current + 1
		fn, ok := transformer.Links[VersionPair{current, next}]
		if !ok {
			return nil, fmt.Errorf("no transformer from v%d to v%d", current, next)
		}
		var err error
		out, err = fn(out)
		if err != nil {
			return nil, fmt.Errorf("error transforming from v%d to v%d: %w", current, next, err)
		}
		current = next
	}
	return out, nil
}

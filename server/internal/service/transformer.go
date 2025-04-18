package service

import (
	"errors"
	"fmt"
)

// TransformerFunc transforms data from one version to the next
type TransformerFunc func([]byte) ([]byte, error)

// VersionPair identifies the path from currentVersion -> nextVersion
type VersionPair struct {
	From string
	To   string
}

// TransformerChain represents a transformer that can perform linked transformation operations
type TransformerChain struct {
	LatestVersion string
	Links         map[VersionPair]TransformerFunc
}

// TransformUp automatically migrates data from `fromVer` to LatestVersion
func (t *TransformerChain) TransformUp(fromVer string, data []byte) ([]byte, error) {
	path, err := t.findPath(fromVer, t.LatestVersion)
	if err != nil {
		return nil, err
	}

	out := data
	for i := 0; i < len(path)-1; i++ {
		vp := VersionPair{From: path[i], To: path[i+1]}
		fn, ok := t.Links[vp]
		if !ok {
			return nil, fmt.Errorf("missing transformer from %s to %s", vp.From, vp.To)
		}
		out, err = fn(out)
		if err != nil {
			return nil, fmt.Errorf("error transforming from %s to %s: %w", vp.From, vp.To, err)
		}
	}
	return out, nil
}

// findPath finds a path from `from` to `to` using BFS
func (t *TransformerChain) findPath(from, to string) ([]string, error) {
	graph := make(map[string][]string)
	for vp := range t.Links {
		graph[vp.From] = append(graph[vp.From], vp.To)
	}

	// BFS
	type state struct {
		version string
		path    []string
	}
	queue := []state{{from, []string{from}}}
	visited := make(map[string]bool)

	for len(queue) > 0 {
		cur := queue[0]
		queue = queue[1:]

		if cur.version == to {
			return cur.path, nil
		}
		if visited[cur.version] {
			continue
		}
		visited[cur.version] = true

		for _, neighbor := range graph[cur.version] {
			if !visited[neighbor] {
				queue = append(queue, state{neighbor, append(cur.path, neighbor)})
			}
		}
	}

	return nil, errors.New("no path found from " + from + " to " + to)
}

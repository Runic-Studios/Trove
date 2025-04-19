package service

import (
	"errors"
	"fmt"
)

// TransformerFunc transforms the blob for a specific table & column,
// from one version to the next.
type TransformerFunc func(table, column string, data []byte) ([]byte, error)

// VersionPair identifies a single step From→To
type VersionPair struct {
	From string
	To   string
}

// TransformerChain knows the latest schema version and how to hop through every intermediate step.
type TransformerChain struct {
	LatestVersion string
	Links         map[VersionPair]TransformerFunc
}

// TransformUp migrates the given table.column blob from `fromVer` all the way to LatestVersion.
func (t *TransformerChain) TransformUp(
	table, column, fromVer string,
	data []byte,
) ([]byte, error) {
	// figure out the version‑chain: [fromVer, v2, v3, ..., LatestVersion]
	path, err := t.findPath(fromVer, t.LatestVersion)
	if err != nil {
		return nil, err
	}

	out := data
	for i := 0; i < len(path)-1; i++ {
		step := VersionPair{From: path[i], To: path[i+1]}
		fn, ok := t.Links[step]
		if !ok {
			return nil, fmt.Errorf(
				"missing transformer for %s.%s from %s → %s",
				table, column, step.From, step.To,
			)
		}
		out, err = fn(table, column, out)
		if err != nil {
			return nil, fmt.Errorf(
				"error transforming %s.%s %s → %s: %w",
				table, column, step.From, step.To, err,
			)
		}
	}
	return out, nil
}

// findPath does a simple BFS only over version strings to discover a path.
func (t *TransformerChain) findPath(from, to string) ([]string, error) {
	graph := make(map[string][]string)
	for vp := range t.Links {
		graph[vp.From] = append(graph[vp.From], vp.To)
	}

	type state struct {
		version string
		path    []string
	}
	queue := []state{{from, []string{from}}}
	visited := map[string]bool{}

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

		for _, next := range graph[cur.version] {
			if !visited[next] {
				queue = append(queue, state{next, append(cur.path, next)})
			}
		}
	}

	return nil, errors.New("no path found from " + from + " to " + to)
}

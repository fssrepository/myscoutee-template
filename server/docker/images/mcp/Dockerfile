# syntax=docker/dockerfile:1.14

ARG MCP_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-mcp:1.5.0@sha256:42f048dd06c0e78727b46db45b80b2790112ae63e8bcd82e84542eb7eb5af9c2
FROM ${MCP_BASE_IMAGE}
# Public config must be readable and its directory traversable by UID 65532.
COPY --chmod=u=rwX,go=rX product.json contract.json /etc/mcp/

# Development TLS credentials

The native nginx entrypoint creates a project-local self-signed certificate on
first startup. The private key and certificate are ignored by Git. Production
certificate provisioning belongs to the installation workflow.

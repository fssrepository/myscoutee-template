# Operator nginx IP blocklist

The pinned shared nginx image owns validation, background refresh and enforcement.
The template supplies its operator list at
`/etc/myscoutee-template/nginx/nginx-blocked-ips.json`; generic installations start
with `{}`. Each exact IPv4/IPv6 address maps to its own nonempty single-line 403
message (up to 1000 UTF-8 bytes). The list is operator-managed.

```json
{
  "192.0.2.1": "Access denied.",
  "2001:db8::2": "Access blocked for this address."
}
```

These are documentation addresses, not a default deny list. Remove an entry to
unblock it; `{}` clears the manual list. The image applies changes through its
native background refresh, without restarting or reloading nginx. The previous
Python worker and systemd timer have been retired.

Scanner findings and traffic state use `myscoutee-template-nginx-state-v1`.
Normal package removal and upgrades preserve operator state; the configured
purge policy removes the package-owned volume. Removing a manual entry does
not clear an independent scanner ban in that volume.

Implementation, validation rules and focused container tests belong to the
[shared nginx repository](https://github.com/fssrepository/myscoutee-nginx).
Shared packaging lifecycle documentation is in the pinned
[packaging dependency](../../../config/packaging/node_modules/@fssrepository/myscoutee-packaging/docs/production.md).

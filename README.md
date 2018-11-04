# Simple ring server for NixOs

Designed as systemd service for triggering shell scripts from CI-hooks or other kind of webhooks trough `nginx`.

(in progress)


## Config file

```Clojure
{:port 1988 :host "localhost" :endpoints {
   "/foo" {:script "echo FOO" :gitlab-token "SEACRET"}
   "/bar" {:shell "/path/to/shell.sh"}}}
```

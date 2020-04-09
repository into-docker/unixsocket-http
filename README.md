# unixsocket-http

[![Clojars Project](https://img.shields.io/clojars/v/unixsocket-http.svg)](https://clojars.org/unixsocket-http)

**unixsocket-http** is a Clojure library to handle HTTP communication over
UNIX domain sockets. This kind of I/O is notably used by the [Docker][docker]
daemon which was the main driver to create this library.

[docker]: https://www.docker.com/

## Usage

Use the `unixsocket-http.core` namespace to access HTTP functionality with
a similar API as [clj-http][].

```clojure
(require '[unixsocket-http.core :as uhttp])
(def client (uhttp/client "/var/run/docker.sock"))
```

Note that this project does not have the ambition to replicate all of clj-http's
functionality. The main use case is communication with Docker which will
naturally restrict the feature set implemented.

[clj-http]: https://github.com/dakrone/clj-http

Once you have a client, you can send requests:

```clojure
(uhttp/get client "/_ping")
;; {:status 200,
;;  :headers
;;  {"api-version" "1.40",
;;   "server" "Docker/19.03.2 (linux)",
;;   "content-type" "text/plain; charset=utf-8",
;;   "content-length" "2",
;;   "docker-experimental" "false",
;;   "pragma" "no-cache",
;;   "date" "Thu, 09 Apr 2020 15:20:06 GMT",
;;   "ostype" "linux",
;;   "cache-control" "no-cache, no-store, must-revalidate"},
;;  :body "OK"}
```

All HTTP functions take an options map as it's last parameter that can be used
to supply additional data or alter the request/response behaviour.

### Query Parameters & Body

Query parameters can be passed using `:query-params`, and a body can be
supplied as either `InputStream` or `String` using the `:body` key.

```clojure
(uhttp/post
  client
  "/images/create"
  {:query-params {:fromImage "node:alpine", :repo "testnode", :tag "latest"}})
;; {:status 200,
;;  :headers
;;  {"api-version" "1.40",
;;   "content-type" "application/json",
;;   "date" "Thu, 09 Apr 2020 15:27:11 GMT",
;;   "docker-experimental" "false",
;;   "ostype" "linux",
;;   "server" "Docker/19.03.2 (linux)",
;;   "transfer-encoding" "chunked"},
;;  :body
;;  "{\"status\":\"Pulling from library/node\",\"id\":\"latest\"}\r\n..."}
```

### Streaming Responses

Use `:as :stream` in the options map to make `:body` an `InputStream` to
consume from.

### Exceptions

You can set `:throw-exceptions` to `false` in the options map to prevent the
HTTP client from throwing an exception.

## License

```
MIT License

Copyright (c) 2020 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

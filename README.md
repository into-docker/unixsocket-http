# unixsocket-http

[![Clojars Project](https://img.shields.io/clojars/v/unixsocket-http.svg)](https://clojars.org/unixsocket-http)
[![Documentation](https://cljdoc.org/badge/unixsocket-http/unixsocket-http)](https://cljdoc.org/d/unixsocket-http/unixsocket-http/CURRENT)
[![CI](https://github.com/into-docker/unixsocket-http/workflows/CI/badge.svg)](https://github.com/into-docker/unixsocket-http/actions?query=workflow%3ACI)
[![codecov](https://codecov.io/gh/into-docker/unixsocket-http/branch/master/graph/badge.svg?token=GLSK1G95TX)](https://codecov.io/gh/into-docker/unixsocket-http)
[![Compatible with GraalVM](https://img.shields.io/badge/graalvm-compatible-success)](https://www.graalvm.org/docs/reference-manual/native-image)

**unixsocket-http** is a Clojure library to handle HTTP communication over
UNIX domain sockets. This kind of I/O is notably used by the [Docker][docker]
daemon which was the main driver to create this library.

[docker]: https://www.docker.com/

## Usage

Use the `unixsocket-http.core` namespace to access HTTP functionality with
a similar API as [clj-http][].

```clojure
(require '[unixsocket-http.core :as uhttp])
(def client (uhttp/client "unix:///var/run/docker.sock"))
```

To provide a common API towards TCP sockets, they are also supported:

```clojure
(def client (uhttp/client "tcp://127.0.0.1:6537"))
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

All HTTP functions take an options map as their last parameter that can be used
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

Use `:as :stream` in the options map to make `:body` an `java.io.InputStream` to
consume from. Alternatively, use `:as :socket` to get access to the underlying
`java.net.Socket` for bidirectional communication.

Always make sure to call `close` on the resources obtained this way, otherwise
you'll run into connection leaks.

### Exceptions

By default, HTTP status codes ≥ 400 will cause an exception
(`clojure.lang.ExceptionInfo`) to be thrown. You can access the underlying
response via `ex-data`.

Note that the `:body` will not be present if you are expecting a streaming
response, unless you explicitly set `:throw-entire-message?` to `true`.

If you want to prevent the client from throwing exceptions, and you'd rather get
the response no matter what, you can set `:throw-exceptions?` to `false`.

### TLS

You can create a client with an `https://...` URL, which will prompt it to use
TLS to perform any requests. Usually, the relevant certificates should be
present in your Java keystore and the underlying `OkHttpClient` will pick them
up automatically when verifying the connection.

For _mutual_ TLS (mTLS) or pointers on how to tackle TLS setup manually, check
out the [respective documentation](./doc/01-mTLS.md).

## GraalVM

This library can be used with GraalVM's [`native-image`][native-image] tool to
create native Clojure executables. The necessary configuration files are already
bundled with this library and should be picked up automatically.

[native-image]: https://www.graalvm.org/docs/reference-manual/native-image/

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

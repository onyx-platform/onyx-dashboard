# onyx-dashboard

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/lbradstreet/onyx-dashboard?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A dashboard for the Onyx distributed computation system
(https://github.com/MichaelDrogalis/onyx). 

## Development

Setup environment variables for `HORNETQ_PORT`, `HORNETQ_HOST`, and `ZOOKEEPER_ADDR` in your lein user profiles.clj like so:

```
{:user {:env {:hornetq-port 5445
	      :hornetq-host "54.44.41.99"
	      :zookeeper-addr "54.44.229.123:2181,54.44.240.52:2181"}
```

or by setting the environment variables in your shell.

Then open a terminal and type `lein figwheel` to start the Figwheel build
(https://github.com/bhauman/lein-figwheel).

and run `lein repl` to start your repl.

In the REPL, type

```clojure
(user/go)
```

Then point your browser at http://localhost:3000/

## License

Copyright © 2014 Lucas Bradstreet

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.7.0-SNAPSHOT (ecadc3ce).

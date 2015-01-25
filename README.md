# onyx-dashboard

A dashboard for the Onyx distributed computation system
(https://github.com/MichaelDrogalis/onyx). 

## Development



Open a terminal and type `lein figwheel` to start the Figwheel build
(https://github.com/bhauman/lein-figwheel).

Set env variables for HORNETQ_HOST, HORNETQ_PORT and ZOOKEEPER_ADDR (in the
usual environ (https://github.com/weavejester/environ) way, can be in profile,
shell environment or java properties)

and run
start your repl.

In the REPL, type

```clojure
(user/go)
```

Then point your browser at http://localhost:3000/

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.7.0-SNAPSHOT (ecadc3ce).

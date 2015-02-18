# onyx-dashboard

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/lbradstreet/onyx-dashboard?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A dashboard for the Onyx distributed computation system
(https://github.com/MichaelDrogalis/onyx). 

## Versioning

Version numbers will kept in sync with [Onyx]
(https://github.com/MichaelDrogalis/onyx). For example, to use a version
compatible with Onyx 0.5.2, use a version fo the dashboard beginning with
0.5.2. The fourth version number will be reserved for dashboard versioning, in
order to provide releases out of band with Onyx.

## Development

Setup environment variables for `HORNETQ_PORT`, `HORNETQ_HOST`, and
`ZOOKEEPER_ADDR` in your lein user profiles.clj like so:

```
{:user {:env {:hornetq-port 5445
	      :hornetq-host "54.44.41.99"
	      :zookeeper-addr "54.44.229.123:2181,54.44.240.52:2181"}
```

or by setting the environment variables in your shell.

Then open a terminal and run `lein figwheel` to start the Figwheel build
(https://github.com/bhauman/lein-figwheel).

and run `lein repl` to start your repl.

In the REPL, run

```clojure
(user/go)
```

Then point your browser at http://localhost:3000/

## Deployment

Configure environment variables for `HORNETQ_PORT`, `HORNETQ_HOST`, and `ZOOKEEPER_ADDR`.

e.g. `HORNETQ_PORT=5445 HORNETQ_HOST="54.44.41.99" ZOOKEEPER_ADDR="54.44.229.123:2181,54.44.240.52:2181"` 

Then run the Onyx Dashboard in one of several ways:

* Download and run the [uberjar
  version](https://s3.amazonaws.com/onyx-dashboard/onyx-dashboard-0.5.2.0.jar).
  Then run it via `java -jar FILENAME`.
* Build and run the uberjar ``` lein clean && lein with-profile uberjar uberjar``. 
Then run it via `java -jar target/onyx-dashboard.jar`.
* Use the development instructions above.
<!--* Run a copy using docker, using our repository at
  [Dockerhub](https://registry.hub.docker.com/u/onyx/onyx-dashboard/) ensuring
  to set the environment variables described above via docker `-e`.-->


## License

Copyright © 2014 Lucas Bradstreet & Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.7.0-SNAPSHOT (ecadc3ce).

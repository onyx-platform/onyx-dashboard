(defproject org.onyxplatform/onyx-dashboard "0.9.9.1-SNAPSHOT"
  :description "Dashboard for the Onyx distributed computation system"
  :url "http://github.com/lbradstreet/onyx-dashboard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :test-paths ["spec/clj" "test"]

  :java-opts ["-Xmx2g" "-server"]

  :main onyx-dashboard.system

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.34" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/sente "1.8.1" :exclusions [com.taoensso/timbre com.taoensso/encore]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [ring "1.3.2"]
                 ^{:voom {:repo "git@github.com:onyx-platform/onyx.git" :branch "master"}}
                 [org.onyxplatform/onyx "0.9.9"]
                 [org.onyxplatform/lib-onyx "0.9.7.1" :exclusions [ring-jetty-component org.onyxplatform/onyx]]
                 [org.onyxplatform/onyx-visualization "0.1.0"]
                 [timothypratley/patchin "0.3.5"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [cljsjs/moment "2.9.0-0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.3.4"]
                 ;; Fixme, need to pin instaparse for some reason
                 ;; deps :tree says that compojure is bringing a compatible version
                 ;; in and I can't figure it out
                 [instaparse "1.4.1"]
                 [enlive "1.1.5"]
                 [fence "0.2.0"]
                 [fipp "0.6.4"]
                 [environ "1.0.0"]
                 [http-kit "2.1.19"]
                 [org.apache.httpcomponents/httpcore "4.4.4"]
                 [org.clojure/core.cache "0.6.4"]
                 [shoreleave/shoreleave-browser "0.3.0"]
                 [org.omcljs/om "1.0.0-alpha32"]
                 [ankha "0.1.5.1-479897" :exclusions [om com.cemerick/austin]]
                 [racehub/om-bootstrap "0.6.1" :exclusions [om]]
                 [prismatic/om-tools "0.4.0" :exclusions [om]]

;;; newdeps
                 [navis/untangled-client "0.4.10"]
                 [navis/untangled-server "0.4.8" :exclusions [io.aviso/pretty org.clojure/clojurescript]]
                 [navis/untangled-datomic "0.4.9" :exclusions [org.clojure/tools.cli]]
                 [secretary "1.2.3" :exclusions [com.cemerick/clojurescript.test]]
                 [joda-time "2.9.3"]
                 [clj-time "0.11.0"]
                 [lein-doo "0.1.6" :scope "test" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/tools.namespace "0.2.11"]
                 [commons-codec "1.10"]
                 [com.taoensso/timbre "4.3.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [navis/untangled-spec "0.3.6" :scope "test"]
                 [navis/untangled-websockets "0.2.0"]]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-doo "0.1.6" :exclusions [org.clojure/tools.reader]]
            [lein-environ "1.0.0"]
            [navis/untangled-lein-i18n "0.1.2" :exclusions [org.codehaus.plexus/plexus-utils org.clojure/tools.cli org.apache.maven.wagon/wagon-provider-api]]]


  :doo {:build "automated-tests"
        :paths {:karma "node_modules/karma/bin/karma"}}

  :min-lein-version "2.5.0"

  :uberjar-name "onyx-dashboard.jar"
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target" "i18n/out"]

  :untangled-i18n {:default-locale        "en-US"
                   :translation-namespace onyx-dashboard.i18n
                   :source-folder         "src/client"
                   :target-build          "i18n"}

  :source-paths ["dev/server" "dev/watcher" "src/client" "src/server" "specs/client" "specs/server"]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["dev/client" "src/client"]
                        :figwheel true
                        :compiler {:main "cljs.user"
                                   :asset-path "js/compiled/dev"
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/dev"
                                   :recompile-dependents true
                                   :optimizations :none}}
                       {:id "i18n"
                        :source-paths ["src/client"]
                        :compiler {:main "onyx-dashboard.main"
                                   :output-to "i18n/out/compiled.js"
                                   :output-dir "i18n/out"
                                   :optimizations :whitespace}}
                       {:id           "test"
                        :source-paths ["src/client" "specs/client"]
                        :figwheel     {:on-jsload "onyx-dashboard.test-runner/on-load"}
                        :compiler     {:main                 "onyx-dashboard.test-runner"
                                       :asset-path           "js/compiled/specs"
                                       :recompile-dependents true
                                       :output-to            "resources/public/js/compiled/onyx-dashboard-specs.js"
                                       :output-dir           "resources/public/js/compiled/specs"}}
                       {:id           "automated-tests"
                        :source-paths ["src/client" "specs/client"]
                        :compiler     {:output-to     "resources/private/js/unit-tests.js"
                                       :main onyx-dashboard.all-tests
                                       :asset-path    "js"
                                       :output-dir    "resources/private/js"
                                       :optimizations :none}}

                       {:id           "support"
                        :source-paths ["src/client"]
                        :figwheel     true
                        :compiler     {:main                 "onyx-dashboard.support-viewer"
                                       :asset-path           "js/compiled/support"
                                       :output-to            "resources/public/js/compiled/support.js"
                                       :output-dir           "resources/public/js/compiled/support"
                                       :recompile-dependents true
                                       :optimizations        :none}}

                       {:id           "production"
                        :source-paths ["src/client"]
                        :compiler     {:verbose         true
                                       :output-to       "resources/public/js/compiled/onyx-dashboard.min.js"
                                       :output-dir      "resources/public/js/compiled"
                                       :pretty-print    false
                                       :closure-defines {goog.DEBUG false}
                                       :source-map      "resources/public/js/compiled/onyx-dashboard.min.js.map"
                                       :elide-asserts   true
                                       :optimizations   :advanced}}]}

  :figwheel {:css-dirs    ["resources/public/css"]
             :server-port 2345}
                                        ;:hooks [leiningen.cljsbuild]


  :profiles {:dev {:repl-options {:init-ns          user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                                  :port             7001}
                   :env          {:dev true}
                   :dependencies [[figwheel-sidecar "0.5.3-1" :exclusions [ring/ring-core]]
                                  [juxt/dirwatch "0.2.3"]
                                  [binaryage/devtools "0.6.1" :exclusions [environ]]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.12"]]}})

(defproject com.github.redhatqe/polarizer-vertx "0.3.1-SNAPSHOT"
  :description "A service to upload data to Polarion"
  :url "https://github.com/RedHatQE/polarizer-vert"
  :license {:name "GPL-3.0"
            :comment "GNU General Public License v3.0"
            :url "https://choosealicense.com/licenses/gpl-3.0"
            :year 2024
            :key "gpl-3.0"}
  :java-source-path "src"
  :java-source-paths ["src"]
  :main io.vertx.core.Launcher
  :manifest {"Main-Verticle" com.github.redhatqe.polarizer.verticles.MainVerticle}
  :uberjar-merge-with {"META-INF/services/io.vertx.core.spi.VerticleFactory" [slurp str spit]}
  :dependencies [
    [io.vertx/vertx-web-client "3.5.0"]
    [io.vertx/vertx-core "3.5.0"]
    [io.vertx/vertx-web "3.5.0"]
    [io.vertx/vertx-amqp-bridge "3.5.0"]
    [io.vertx/vertx-rx-java2 "3.5.0"]
    [io.vertx/vertx-unit "3.5.0"]
    [io.vertx/vertx-auth-oauth2 "3.5.0"]
    [io.reactivex.rxjava2/rxjava "2.1.6"]
    [org.slf4j/slf4j-simple "1.7.36"]	
    [com.github.redhatqe/polarizer-reporter "0.3.0-SNAPSHOT"]
    [com.github.redhatqe/polarizer-polarizer "0.3.1-SNAPSHOT"]]
  :javac-options {:debug "on"}
  :plugins [[lein2-eclipse "2.0.0"]]
  :profiles {:uberjar {:aot :all}})

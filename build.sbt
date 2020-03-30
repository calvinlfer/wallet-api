name := "wallet-api"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= {
  val akka  = "com.typesafe.akka"
  val akkaV = "2.6.4"
  Seq(
    akka                    %% "akka-actor-typed"            % akkaV,
    akka                    %% "akka-cluster-sharding-typed" % akkaV,
    akka                    %% "akka-persistence-typed"      % akkaV,
    akka                    %% "akka-persistence-query"      % akkaV,
    "com.github.dnvriend"   %% "akka-persistence-jdbc"       % "3.5.3",
    "com.github.pureconfig" %% "pureconfig"                  % "0.12.3",
    "dev.zio"               %% "zio"                         % "1.0.0-RC18-2"
  )
}

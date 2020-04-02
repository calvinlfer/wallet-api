name := "wallet-api"

version := "0.1"

scalaVersion := "2.13.1"

scalacOptions ++= Seq(
  "-deprecation",                              // Emit warning and location for usages of deprecated APIs.
  "-explaintypes",                             // Explain type errors in more detail.
  "-feature",                                  // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",                    // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",             // Allow macro definition (besides implementation and application)
  "-language:higherKinds",                     // Allow higher-kinded types
  "-language:implicitConversions",             // Allow definition of implicit functions called views
  "-unchecked",                                // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                               // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                          // Fail the compilation if there are any warnings.
  "-Xlint:adapted-args",                       // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant",                           // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",                 // Selecting member of DelayedInit.
  "-Xlint:doc-detached",                       // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",                       // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                          // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",               // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",                   // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",                       // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",                    // Option.apply used implicit view.
  "-Xlint:package-object-classes",             // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",             // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",                     // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                        // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",              // A local type parameter shadows a type already in scope.
  "-Ywarn-dead-code",                          // Warn when dead code is identified.
  "-Ywarn-extra-implicit",                     // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",                      // Warn when numerics are widened.
  "-Ywarn-unused:implicits",                   // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",                     // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",                      // Warn if a local definition is unused.
  "-Ywarn-unused:params",                      // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",                     // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",                    // Warn if a private member is unused.
  "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
  "-Ycache-macro-class-loader:last-modified"   // and macro definitions. This can lead to performance improvements.
)

libraryDependencies ++= {
  val akka  = "com.typesafe.akka"
  val akkaV = "2.6.4"

  Seq(
    akka                    %% "akka-actor-typed"            % akkaV,
    akka                    %% "akka-cluster-sharding-typed" % akkaV,
    akka                    %% "akka-persistence-typed"      % akkaV,
    akka                    %% "akka-persistence-query"      % akkaV,
    akka                    %% "akka-serialization-jackson"  % akkaV,
    "com.github.enalmada"   %% "akka-kryo-serialization"     % "1.1.3",
    akka                    %% "akka-http"                   % "10.1.11",
    "com.github.dnvriend"   %% "akka-persistence-jdbc"       % "3.5.3",
    "com.swissborg"         %% "lithium"                     % "0.11.0",
    "de.heikoseeberger"     %% "akka-http-circe"             % "1.31.0",
    "io.circe"              %% "circe-generic"               % "0.12.3",
    "com.github.pureconfig" %% "pureconfig"                  % "0.12.3",
    "dev.zio"               %% "zio"                         % "1.0.0-RC18-2",
    "ch.qos.logback"        % "logback-classic"              % "1.2.3",
    "org.postgresql"        % "postgresql"                   % "42.2.11",
    "org.flywaydb"          % "flyway-core"                  % "6.3.2"
  )
}

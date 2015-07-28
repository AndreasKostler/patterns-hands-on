//   Copyright 2014 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

uniform.project("patterns", "au.com.cba.omnia.patterns")

uniformDependencySettings

libraryDependencies :=
  depend.hadoopClasspath ++
  depend.scaldingproject() ++
  depend.scalaz() ++
  depend.omnia("eventually-api", "1.2.0-20150728133452-d550ee9") ++
  depend.omnia("eventually-ferenginar", "1.2.0-20150728133452-d550ee9") ++
  Seq(
    "au.com.cba.omnia" %% "omnitool-core" % "1.1.0-20140624012128-0b6add6",
    "au.com.cba.omnia" %% "omnitool-time" % "1.1.0-20140624012128-0b6add6",
    "au.com.cba.omnia" %% "omnia-test"    % "2.1.0-20140604032817-d3b19f6" % "test"
  )

uniformAssemblySettings

uniform.docSettings("https://github.com/CommBank/patterns")

uniform.ghsettings

{ stdenv, clojure, makeWrapper, fetchMavenArtifact }:

let cljdeps = import ./deps.nix {};
    classp  = cljdeps.makeClasspaths {};
    version = "1.0.0";

in stdenv.mkDerivation rec {

  name = "simpleRing-${version}";

  src = ./simple_ring.clj;

  buildInputs = [ makeWrapper ];

  phases = ["installPhase"];

  installPhase = ''

      mkdir -p $out/bin

      cp ${src} $out/bin
      makeWrapper ${clojure}/bin/clojure $out/bin/simpleRing \
        --add-flags "-Scp ${classp} -i ${src} -m simple-ring" \
  '';
}

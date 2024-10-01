{ pkgs, lib, config, inputs, ... }:

{
  cachix.enable = false;

  # https://devenv.sh/basics/
  # env.GREET = "devenv";

  # https://devenv.sh/packages/
  packages = [ 
  	# pkgs.git 
  ];

  # https://devenv.sh/languages/
  # languages.rust.enable = true;

  languages.scala = {
	enable = true;
	package = pkgs.scala_3;
	sbt = {
		enable = true;
	};
  };


  # https://devenv.sh/processes/
  # processes.cargo-watch.exec = "cargo-watch";

  # https://devenv.sh/services/
  # services.postgres.enable = true;

  # https://devenv.sh/scripts/
  #scripts.hello.exec = ''
  #  echo hello from $GREET
  #'';

  enterShell = ''
  	echo "~~~ designer shell ~~~"
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
	sbt test
  '';

  # https://devenv.sh/pre-commit-hooks/
  # pre-commit.hooks.shellcheck.enable = true;

  # See full reference at https://devenv.sh/reference/options/
}

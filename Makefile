.PHONY: help
 help:	                                       ## Show list of available make targets
	@cat Makefile | grep -e "^[a-zA-Z_\-]*: *.*## *" | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'


.PHONY: run
run:                                           ## Run the application
	lein cljfmt check
	lein run

.PHONY: format
format:                                        ## Reformat Clojure source code
	lein cljfmt fix

.PHONY: package
package:                                       ## Package a new release of the application
	lein clean
	lein compile
	lein release patch

.PHONY: clean
clean:                                         ## Clean the local build directory
	lein clean

.PHONY: clean-all
clean-all: clean                               ## Clean everything, including the local database state
	rm -rfv local-db/*

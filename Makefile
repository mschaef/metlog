.PHONY: run
run:
	lein run

.PHONY: uberjar
uberjar:
	lein uberjar

.PHONY: run-uberjar
run-uberjar: uberjar
	java -Dconf=local-config.edn -jar ./target/uberjar/metlog-standalone.jar

.PHONY: package
package:
	lein clean
	lein release patch

.PHONY: clean
clean:
	lein clean
	rm -f *~

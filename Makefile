.PHONY: build test package redis-up redis-down redis-status run smoke clean
build test:
	mvn -B test
package:
	mvn -B package
redis-up:
	./scripts/redis-cluster.sh up
redis-down:
	./scripts/redis-cluster.sh down
redis-status:
	./scripts/redis-cluster.sh status
run:
	java -jar target/rate-limiter.jar
smoke:
	./scripts/smoke-test.sh
clean:
	mvn clean

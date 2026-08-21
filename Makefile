CONFIG_FILE := config/config.yml
CONFIG_EXAMPLE := config/config.example.yml

.PHONY: config dev frontend frontend-install up start stop restart build rebuild test clean logs ps check

config:
	powershell -NoProfile -ExecutionPolicy Bypass -Command "if (-not (Test-Path '$(CONFIG_FILE)')) { Copy-Item '$(CONFIG_EXAMPLE)' '$(CONFIG_FILE)'; Write-Host 'Created $(CONFIG_FILE). Review it before starting.' } else { Write-Host 'Using existing $(CONFIG_FILE).' }"

dev: up frontend

frontend:
	npm.cmd --prefix frontend run dev

frontend-install:
	npm.cmd --prefix frontend install

up: config
	docker compose up -d --build

start: up

build:
	docker compose build
	$(MAKE) dev

stop:
	docker compose down

restart: stop up

test:
	docker compose run --rm tests mvn clean test jacoco:report
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/coverage-summary.ps1

rebuild:
	docker compose down -v
	$(MAKE) up

clean:
	docker compose down -v

logs:
	docker compose logs -f backend

ps:
	docker compose ps

check: config
	docker compose config --quiet

CONFIG_FILE := config/config.yml
CONFIG_EXAMPLE := config/config.example.yml

# One Compose project per worktree: the main checkout keeps "identity-server", worktree <name> uses "identity-server-<name>".
WORKTREE := $(notdir $(CURDIR))
COMPOSE_PROJECT_NAME ?= $(if $(filter identity-server,$(WORKTREE)),identity-server,identity-server-$(WORKTREE))
export COMPOSE_PROJECT_NAME

.PHONY: install config dev frontend up start stop restart build rebuild test clean logs ps check

# Worktree init (orca): local config files, portal dependencies and image build, without starting anything.
install:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/worktree-install.ps1
	npm.cmd --prefix frontend ci
	docker compose build

config:
	powershell -NoProfile -ExecutionPolicy Bypass -Command "if (-not (Test-Path '$(CONFIG_FILE)')) { Copy-Item '$(CONFIG_EXAMPLE)' '$(CONFIG_FILE)'; Write-Host 'Created $(CONFIG_FILE). Review it before starting.' } else { Write-Host 'Using existing $(CONFIG_FILE).' }"

# Backend and database in Docker, then the portal in the foreground.
dev: up frontend

# --strictPort: the backend redirects to the portal on 5180 (identity.auth-frontend-url), another port breaks login.
frontend:
	npm.cmd --prefix frontend run dev -- --strictPort

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

# Worktree archive (orca): removes this project's containers, volumes and local images, and the portal dependencies.
# No PowerShell variables here: the shell that runs the recipe would expand "$name" before PowerShell sees it.
clean:
	docker compose down -v --remove-orphans --rmi local
	powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path 'frontend/node_modules') { Remove-Item -Recurse -Force 'frontend/node_modules' }; if (Test-Path 'frontend/dist') { Remove-Item -Recurse -Force 'frontend/dist' }"

logs:
	docker compose logs -f backend

ps:
	docker compose ps

check: config
	docker compose config --quiet

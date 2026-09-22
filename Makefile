.PHONY: release-notes help check-prerequisites

# Default target
help:
	@echo "Usage:"
	@echo "  make release-notes FROM=<tag> TO=<tag>"
	@echo ""
	@echo "Example:"
	@echo "  make release-notes FROM=cereal-client/1.0.0 TO=cereal-client/1.1.0"
	@echo ""
	@echo "This will generate Discord-friendly release notes for end users"
	@echo "based on the code changes between the two specified git tags."
	@echo ""
	@echo "Prerequisites:"
	@echo "  - git (for accessing repository history)"
	@echo "  - GitHub Copilot CLI (install from: https://github.com/github/gh-copilot)"

# Check if required tools are installed
check-prerequisites:
	@echo "Checking prerequisites..."
	@command -v git >/dev/null 2>&1 || { echo "Error: git is not installed. Please install git first."; exit 1; }
	@command -v copilot >/dev/null 2>&1 || { echo "Error: GitHub Copilot CLI is not installed. Install from: https://github.com/github/gh-copilot"; exit 1; }
	@echo "✓ All prerequisites are met!"
	@echo ""

# Generate release notes using GitHub Copilot CLI
release-notes: check-prerequisites
	@if [ -z "$(FROM)" ] || [ -z "$(TO)" ]; then \
		echo "Error: Both FROM and TO tags must be specified."; \
		echo "Usage: make release-notes FROM=<tag> TO=<tag>"; \
		exit 1; \
	fi
	@echo "Generating release notes from $(FROM) to $(TO)..."
	@echo ""
	@git diff $(FROM)..$(TO) | copilot -p "$$(cat prompts/release-notes.txt | sed 's/"/\\"/g')" --silent

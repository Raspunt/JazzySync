# Makefile для JazzySync (Maven + Java 21)

MVN    := mvn
JAR    := target/jazzysync-1.0-SNAPSHOT.jar
MAIN   := io.github.jazzysync.Main

.PHONY: all build test run clean package install install-local uninstall uninstall-local maven-install compile verify help list sync check dist

# Цель по умолчанию
all: package

# sudo drops JAVA_HOME, so if it is unset or has no jlink, pick the first JDK 21+ in /usr/lib/jvm
ifeq ($(and $(JAVA_HOME),$(wildcard $(JAVA_HOME)/bin/jlink)),)
JAVA_HOME := $(shell for d in /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/*; do \
	[ -x "$$d/bin/jlink" ] && "$$d/bin/java" -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])' && { echo "$$d"; break; }; \
	done)
endif
JLINK     ?= $(JAVA_HOME)/bin/jlink
JDEPS     ?= $(JAVA_HOME)/bin/jdeps
DIST_JAR  := dist/jazzy.jar
DIST_JRE  := dist/jre/bin/java

## Компиляция
compile:
	$(MVN) -B compile

## Сборка проекта + тесты
build:
	$(MVN) -B verify

## Запуск тестов
test:
	$(MVN) -B test

## Упаковка fat-jar
package:
	$(MVN) -B package

## Установка в локальный Maven репозиторий
maven-install:
	$(MVN) -B install

## Системная установка в /opt/jazzysync (требует sudo)
install: dist
	@if [ ! -f $(DIST_JAR) ] || [ ! -x $(DIST_JRE) ]; then \
		echo "ERROR: dist/ is incomplete. Run 'make dist' first."; exit 1; \
	fi
	@echo "=== Installing JazzySync to /opt/jazzysync ==="
	@if [ -d /opt/jazzysync ]; then \
		echo "Backing up old installation to /opt/jazzysync.bak"; \
		sudo rm -rf /opt/jazzysync.bak; \
		sudo mv /opt/jazzysync /opt/jazzysync.bak; \
	fi
	sudo cp -r dist /opt/jazzysync
	sudo ln -sf /opt/jazzysync/jazzy /usr/local/bin/jazzy
	@echo "Installed: jazzy -> /opt/jazzysync/jazzy"
	@echo "Run: jazzy --help"

## Локальная установка в ~/.local/bin (без sudo)
install-local: dist
	@if [ ! -f $(DIST_JAR) ] || [ ! -x $(DIST_JRE) ]; then \
		echo "ERROR: dist/ is incomplete. Run 'make dist' first."; exit 1; \
	fi
	@echo "=== Installing JazzySync to ~/.local/jazzysync ==="
	@mkdir -p ~/.local/bin
	@rm -rf ~/.local/jazzysync
	@cp -r dist ~/.local/jazzysync
	@ln -sf ~/.local/jazzysync/jazzy ~/.local/bin/jazzy
	@echo "Installed: ~/.local/bin/jazzy"
	@echo 'Make sure ~/.local/bin is in your PATH'
	@echo "Run: jazzy --help"

## Удаление системной установки
uninstall:
	@echo "=== Removing JazzySync ==="
	@sudo rm -rf /opt/jazzysync /opt/jazzysync.bak
	@sudo rm -f /usr/local/bin/jazzy
	@echo "Uninstalled."

## Удаление локальной установки
uninstall-local:
	@echo "=== Removing local JazzySync ==="
	@rm -rf ~/.local/jazzysync
	@rm -f ~/.local/bin/jazzy
	@echo "Uninstalled."

## Очистка
 clean:
	$(MVN) -B clean

## Полная проверка (clean + verify)
verify: clean
	$(MVN) -B verify

## Сборка портативного дистрибутива (JRE + JAR)
dist: package
	@echo "=== Analyzing modules ==="
	@mkdir -p dist
	@rm -rf dist/jre
	@MODULES=$$($(JDEPS) --print-module-deps $(JAR) | tr -d '\n'); \
	 echo "Required modules: $$MODULES"; \
	 $(JLINK) --add-modules $$MODULES \
	   --strip-debug --no-man-pages --no-header-files \
	   --compress=zip-9 \
	   --output dist/jre
	@cp $(JAR) $(DIST_JAR)
	@echo '#!/bin/bash' > dist/jazzy
	@echo 'SCRIPT_DIR="$$(cd "$$(dirname "$$(readlink -f "$$0")")" && pwd)"' >> dist/jazzy
	@echo 'exec "$$SCRIPT_DIR/jre/bin/java" -jar "$$SCRIPT_DIR/jazzy.jar" "$$@"' >> dist/jazzy
	@chmod +x dist/jazzy
	@echo "=== Distribution ready in dist/ ==="
	@echo "  JRE size: $$(du -sh dist/jre | cut -f1)"
	@echo "  JAR size: $$(du -sh $(DIST_JAR) | cut -f1)"
	@echo "  Run: ./dist/jazzy <command>"

## Список доступных дистрибутивов
list: package
	java -jar $(JAR) list

## Синхронизация всех зеркал
sync: package
	java -jar $(JAR) sync

## Проверка доступности зеркал
check: package
	java -jar $(JAR) check

## Запуск с произвольными аргументами: make run ARGS="sync -d arch"
run: package
	java -jar $(JAR) $(ARGS)

## Справка по командам Makefile
help:
	@echo "Доступные цели:"
	@echo "  all       - сборка fat-jar (по умолчанию)"
	@echo "  build     - компиляция и тесты"
	@echo "  compile   - только компиляция"
	@echo "  test      - запуск тестов"
	@echo "  package   - упаковка в fat-jar"
	@echo "  install       - системная установка в /opt/jazzysync (требует sudo)"
	@echo "  install-local - локальная установка в ~/.local/bin (без sudo)"
	@echo "  uninstall     - удаление системной установки"
	@echo "  uninstall-local - удаление локальной установки"
	@echo "  maven-install - установка в локальный Maven репозиторий"
	@echo "  clean     - очистка артефактов"
	@echo "  verify    - полная проверка (clean + verify)"
	@echo "  dist      - сборка портативного дистрибутива (JRE + JAR)"
	@echo "  list      - список доступных дистрибутивов"
	@echo "  sync      - синхронизация всех зеркал"
	@echo "  check     - проверка доступности зеркал"
	@echo "  run       - запуск с аргументами (make run ARGS=\"sync -d debian\")"
	@echo "  help      - эта справка"

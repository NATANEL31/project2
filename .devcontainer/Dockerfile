FROM ubuntu:22.04

# Build argument for version (passed from docker-compose.yml)
ARG VERSION=dev

ENV DEBIAN_FRONTEND=noninteractive

# Install core tooling and language runtimes required for the course.
# - C/C++: build-essential, cmake, clang
# - Java: Temurin 21 (installed separately below)
# - Python: install Python 3.12 (from deadsnakes PPA) + pip, venv
# - SQLite: sqlite3 client and development headers
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        software-properties-common \
        build-essential \
        cmake \
        clang \
        git \
        wget \
        sudo \
        zsh \
        gnupg2 \
        maven \
        sqlite3 \
        libsqlite3-dev \
        tzdata \
        curl && \
    rm -rf /var/lib/apt/lists/*

# Install GCC/G++ 13 (for C17 and modern C++23 support) from ubuntu-toolchain-r
RUN add-apt-repository -y ppa:ubuntu-toolchain-r/test && \
    apt-get update && \
    apt-get install -y --no-install-recommends gcc-13 g++-13 && \
    update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-13 100 && \
    update-alternatives --install /usr/bin/g++ g++ /usr/bin/g++-13 100 && \
    rm -rf /var/lib/apt/lists/*

# Install Temurin (Eclipse Adoptium) JDK 21
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb jammy main" > /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends temurin-21-jdk && \
    rm -rf /var/lib/apt/lists/*

# Install Python 3.12 from deadsnakes PPA (the devcontainer expects 3.12)
RUN add-apt-repository -y ppa:deadsnakes/ppa && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
      python3.12 \
      python3.12-venv \
      python3.12-dev \
      python3-pip && \
    rm -rf /var/lib/apt/lists/*

# Configure JAVA_HOME and PATH for Java tools in an architecture-neutral way
# Create a stable symlink and set env vars
RUN ln -s "$(dirname $(dirname $(readlink -f $(command -v javac))))" /usr/lib/jvm/java-21 && \
    echo 'export JAVA_HOME=/usr/lib/jvm/java-21' > /etc/profile.d/java.sh && \
    echo 'export PATH=$JAVA_HOME/bin:$PATH' >> /etc/profile.d/java.sh
ENV JAVA_HOME=/usr/lib/jvm/java-21
ENV PATH="/usr/lib/jvm/java-21/bin:$PATH"

# Create a non-root user for students
RUN useradd -m -s /bin/bash -G sudo spl && \
    echo "spl ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/spl && \
    chmod 0440 /etc/sudoers.d/spl

# Remove the sudo hint message that appears on login
# The message is triggered by /etc/bash.bashrc checking for .sudo_as_admin_successful
# We create this file AND also comment out the sudo hint in /etc/bash.bashrc
RUN touch /home/spl/.sudo_as_admin_successful /home/spl/.hushlogin && \
    chown spl:spl /home/spl/.sudo_as_admin_successful /home/spl/.hushlogin && \
    sed -i '/# sudo hint/,/^fi$/d' /etc/bash.bashrc

# Create welcome message for students - add to .bashrc so it shows in terminals
# Store the version in an environment variable
ENV STUDENT_ENV_VERSION=${VERSION}

RUN echo '' >> /home/spl/.bashrc && \
    echo '# Welcome message' >> /home/spl/.bashrc && \
    echo 'if [ -z "$VSCODE_WELCOME_SHOWN" ]; then' >> /home/spl/.bashrc && \
    echo '  export VSCODE_WELCOME_SHOWN=1' >> /home/spl/.bashrc && \
    echo '  echo ""' >> /home/spl/.bashrc && \
    echo '  echo "═══════════════════════════════════════════════════════════"' >> /home/spl/.bashrc && \
    echo '  echo "  Welcome to BGU SPL Student Env (v${STUDENT_ENV_VERSION})"' >> /home/spl/.bashrc && \
    echo '  echo "═══════════════════════════════════════════════════════════"' >> /home/spl/.bashrc && \
    echo '  echo ""' >> /home/spl/.bashrc && \
    echo '  echo "Available tools:"' >> /home/spl/.bashrc && \
    echo '  echo "  • C/C++: gcc-13, g++-13, clang, cmake"' >> /home/spl/.bashrc && \
    echo '  echo "  • Java: OpenJDK 21 (Temurin)"' >> /home/spl/.bashrc && \
    echo '  echo "  • Python: 3.12"' >> /home/spl/.bashrc && \
    echo '  echo "  • SQLite: sqlite3"' >> /home/spl/.bashrc && \
    echo '  echo ""' >> /home/spl/.bashrc && \
    echo '  echo "Happy coding! 🚀"' >> /home/spl/.bashrc && \
    echo '  echo ""' >> /home/spl/.bashrc && \
    echo 'fi' >> /home/spl/.bashrc && \
    chown spl:spl /home/spl/.bashrc

USER spl

WORKDIR /home/spl

# Copy small examples into the image so instructors/students can smoke-test the image
COPY --chown=spl:spl examples /home/spl/examples

# Default to an interactive shell; students can mount a workspace when running
CMD ["bash"]

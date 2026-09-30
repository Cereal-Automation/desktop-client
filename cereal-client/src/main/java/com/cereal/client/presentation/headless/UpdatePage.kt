package com.cereal.client.presentation.headless

/** A read-only screen of lines: the `U` upgrade commands, or the required-update block. */
class UpdatePage(
    override val title: String,
    private val lines: List<String>,
    override val keys: String = "",
) : TuiPage {
    override fun body(
        width: Int,
        height: Int,
    ) = listOf("") + lines.map { "  $it" }

    companion object {
        fun required(
            version: String,
            commands: List<String>,
        ) = UpdatePage("Update required", listOf("Cereal $version is required.", "No tasks can start on this version.", "") + commands)

        fun commands(
            version: String,
            commands: List<String>,
        ) = UpdatePage("Upgrade to $version", commands, keys = "any key back")

        /**
         * How to upgrade this distribution to [version]. Headless mode never downloads or installs
         * anything itself: Docker (`CEREAL_DISTRIBUTION=docker`) pulls and recreates the container,
         * an AppImage (`$APPIMAGE`) or anything else downloads the new build by hand.
         */
        fun upgradeCommands(
            environment: Map<String, String>,
            websiteUrl: String,
            version: String,
        ): List<String> =
            when {
                environment["CEREAL_DISTRIBUTION"] == "docker" -> {
                    listOf(
                        "Pull the new image and recreate the container with the same volume and flags:",
                        "",
                        "  docker pull $DOCKER_IMAGE:$version",
                        "  docker rm -f <container>",
                        "  docker run -it … $DOCKER_IMAGE:$version",
                        "",
                        "With Compose: docker compose pull && docker compose up -d",
                    )
                }

                environment.containsKey("APPIMAGE") -> {
                    listOf(
                        "Download the new AppImage (Cereal $version) from $websiteUrl,",
                        "replace ${environment["APPIMAGE"]} with it, and start it again with --headless.",
                    )
                }

                else -> {
                    listOf("Download Cereal $version from $websiteUrl and start it again with --headless.")
                }
            }

        private const val DOCKER_IMAGE = "ghcr.io/cereal-automation/cereal"
    }
}

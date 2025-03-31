# Skinshuffle Mineskin Proxy

A proxy server used by the [SkinShuffle](https://modrinth.com/mod/skinshuffle) mod - used to protect the mineskin API key from production.

Simply create an API key on the mineskin website, add the `SkinShuffle/Proxy` user agent.

## Usage

Clone the repository, create a `local.properties` in the resources folder, run `gradlew run`. Done! Change the proxy domain and port in your skinshuffle config in your minecraft instance.
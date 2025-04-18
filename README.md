# Skinshuffle Mineskin Proxy

A proxy server used by the [SkinShuffle](https://modrinth.com/mod/skinshuffle) mod - used to protect the mineskin API key from production.

Simply create an API key on the mineskin website, add the `SkinShuffle/Proxy` user agent.

## Usage

Clone the repository, run `gradlew run` with the env variables specified in `.env.example` pre-exported using `export` (bash) or `$Env:... = ...` (windows). Done! Change the proxy domain and port in your skinshuffle config in your minecraft instance.

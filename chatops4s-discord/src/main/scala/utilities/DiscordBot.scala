package utilities

import enums.AcceptDeclineCustomId
import models.{ActionRow, Button, Interaction, InteractionRequest, InteractionResponse, InteractionResponseData, MessageResponse, SlashRegistration}
import sttp.client4.DefaultSyncBackend
import sttp.client4.quick.*
import io.circe.syntax._
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Hex

object DiscordBot {
  private final val rootUrl = "https://discord.com/api/v10"
  private final val applicationId = EnvLoader.get("DISCORD_BOT_APPLICATION_ID")
  private final val guildId = EnvLoader.get("DISCORD_BOT_GUILD_ID")
  private final val botToken = EnvLoader.get("DISCORD_BOT_TOKEN")
  private final val url = EnvLoader.get("DISCORD_BOT_URL")
  private final val versionNumber = 1.0
  private val discordPublicKey = EnvLoader.get("DISCORD_BOT_PUBLIC_KEY")

  private def baseRequest = basicRequest
    .header("Authorization", s"Bot $botToken")
    .header("User-Agent", s"DiscordBot ($url, $versionNumber)")
    .header("Content-Type", "application/json")

  def verifySignature(
     signature: String,
     timestamp: String,
     body: String
  ): Boolean = {
    val publicKeyBytes = Hex.decode(discordPublicKey.strip())
    val signatureBytes = Hex.decode(signature.strip())
    val message = (timestamp.strip() + body.strip()).getBytes("UTF-8")
    val verifier = new Ed25519Signer()
    verifier.init(false, new Ed25519PublicKeyParameters(publicKeyBytes, 0))
    verifier.update(message, 0, message.length)
    verifier.verifySignature(signatureBytes)
  }

  def sendAcceptDeclineMessage(channelId: String, content: String): Unit = {
    val backend = DefaultSyncBackend()
    val message = MessageResponse(
      content = content,
      components = Seq(
        ActionRow(
          `type` = 1,
          components = Seq(
            Button(
              `type` = 2,
              style = 1,
              label = "Accept",
              custom_id = AcceptDeclineCustomId.Accept.value
            ),
            Button(
              `type` = 2,
              style = 2,
              label = "Decline",
              custom_id = AcceptDeclineCustomId.Decline.value
            )
          )
        )
      )
    )
    val body: String = message.asJson.noSpaces
    val response = baseRequest
      .post(uri"$rootUrl/channels/$channelId/messages")
      .body(body)
      .send(backend)
  }

  def sendInteraction(
     incoming: InteractionRequest,
     interaction: Interaction
  ): InteractionResponse = {
    val interactionResponseData = InteractionResponseData(
      content = interaction.content(incoming),
    )
    val interactionResponse = InteractionResponse(
      `type` = interaction.`type`,
      data = Some(interactionResponseData)
    )
    val body = interactionResponseData.asJson.noSpaces
    val backend = DefaultSyncBackend()
    baseRequest
      .post(uri"$rootUrl/interactions/${incoming.id}/${incoming.token}/callback")
      .body(body)
      .contentType("application/json")
      .send(backend)
    interactionResponse
  }

  def sendSlashRegistration(slashRegistration: SlashRegistration): Unit = {
    val body = slashRegistration.asJson.noSpaces
    val response = baseRequest
      .post(uri"$rootUrl/applications/$applicationId/guilds/$guildId/commands")
      .body(body)
      .contentType("application/json")
      .send(backend)
  }
}

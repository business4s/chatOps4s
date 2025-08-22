package models

import io.circe.Codec

case class MessageResponse(
    id: String,
) derives Codec

case class ThreadResponse(
    id: String,
) derives Codec

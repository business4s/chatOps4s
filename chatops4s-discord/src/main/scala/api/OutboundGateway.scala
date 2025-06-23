class OutboundGateway {
  def sendToChannel(channelId: String, message: Message): Unit = {}
  def sendToThread(messageId: String, message: Message): Unit = {}
}
export const SOCKET_ACK_TIMEOUT_MS = 10000

export const emitWithAck = (socket, eventName, payload, timeoutMessage, timeoutMs = SOCKET_ACK_TIMEOUT_MS) => new Promise((resolve, reject) => {
  const timer = globalThis.setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs)
  const ack = (response) => {
    globalThis.clearTimeout(timer)
    resolve(response)
  }

  if (payload === undefined) {
    socket.emit(eventName, ack)
    return
  }

  socket.emit(eventName, payload, ack)
})
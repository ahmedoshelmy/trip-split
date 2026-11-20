import { Client } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'
import type { BalanceSheet } from './api'

export interface LiveMessage {
  type: 'balances' | 'timeline'
  version: number
  balances?: BalanceSheet
}

/** Subscribes to /topic/trips/{id} over STOMP; reconnects automatically. */
export function useTripLive(tripId: string | null, onMessage: (message: LiveMessage) => void) {
  const handler = useRef(onMessage)
  useEffect(() => {
    handler.current = onMessage
  }, [onMessage])

  useEffect(() => {
    if (!tripId) return
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/trips/${tripId}`, (frame) => {
          handler.current(JSON.parse(frame.body) as LiveMessage)
        })
      },
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [tripId])
}

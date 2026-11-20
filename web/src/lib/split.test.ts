import { describe, expect, it } from 'vitest'
import { buildSplit, previewEqual, type SplitRow } from './split'

const rows = (overrides: Partial<SplitRow>[]): SplitRow[] =>
  overrides.map((o, i) => ({ memberId: `m${i}`, included: true, shares: '1', exact: '', ...o }))

describe('buildSplit', () => {
  it('builds an equal split from the ticked members', () => {
    const result = buildSplit('EQUAL', rows([{}, { included: false }, {}]), '90', 'EGP')
    expect(result).toEqual({ ok: true, split: { kind: 'EQUAL', participants: ['m0', 'm2'] } })
  })

  it('needs at least one participant', () => {
    expect(buildSplit('EQUAL', rows([{ included: false }]), '90', 'EGP').ok).toBe(false)
  })

  it('validates shares', () => {
    expect(buildSplit('SHARES', rows([{ shares: '2' }, { shares: '1' }]), '90', 'EGP')).toEqual({
      ok: true,
      split: { kind: 'SHARES', shares: { m0: 2, m1: 1 } },
    })
    expect(buildSplit('SHARES', rows([{ shares: '1.5' }]), '90', 'EGP').ok).toBe(false)
  })

  it('requires exact amounts to add up to the total', () => {
    const ok = buildSplit('EXACT', rows([{ exact: '60' }, { exact: '40.00' }]), '100', 'EGP')
    expect(ok).toEqual({ ok: true, split: { kind: 'EXACT', amounts: { m0: '60', m1: '40.00' } } })
    expect(buildSplit('EXACT', rows([{ exact: '60' }, { exact: '30' }]), '100', 'EGP').ok).toBe(false)
  })
})

describe('previewEqual', () => {
  it('mirrors the server remainder rule', () => {
    expect(previewEqual(1000, 3)).toEqual([334, 333, 333])
    expect(previewEqual(1001, 3)).toEqual([334, 334, 333])
    expect(previewEqual(0, 2)).toEqual([0, 0])
  })
})

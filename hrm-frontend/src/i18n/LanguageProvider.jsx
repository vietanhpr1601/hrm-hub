import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import pages from './en.json'
import layout from './layoutEn'

const en = { ...pages, ...layout }

const STORAGE_KEY = 'hrm-lang'
const ATTRIBUTES = ['placeholder', 'aria-label', 'title']
const I18nContext = createContext({ lang: 'vi', setLang: () => {}, t: (text) => text })

const readLang = () => {
  try { return localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'vi' } catch { return 'vi' }
}

/** Dịch một chuỗi tiếng Việt; giữ khoảng trắng hai đầu và dấu ":" ở cuối. */
export function translate(text, lang) {
  if (lang !== 'en' || !text) return text
  const trimmed = text.trim()
  if (!trimmed) return text
  let result = en[trimmed]
  if (!result && trimmed.endsWith(':')) {
    const base = en[trimmed.slice(0, -1).trim()]
    if (base) result = `${base}:`
  }
  if (!result) return text
  const start = text.indexOf(trimmed)
  return text.slice(0, start) + result + text.slice(start + trimmed.length)
}

/*
 * Các trang hiện có viết thẳng chữ tiếng Việt trong JSX. Thay vì sửa từng trang, khi chọn EN
 * ta theo dõi DOM và thay nội dung chữ khớp từ điển (giống trình dịch của trình duyệt).
 * Bản gốc được nhớ lại để chuyển về VI ngay không cần tải lại trang.
 */
const textMemory = new WeakMap()
const attrMemory = new WeakMap()
const touchedText = new Set()
const touchedAttr = new Set()

function translateText(node, lang) {
  const saved = textMemory.get(node)
  if (saved && node.nodeValue === saved.shown) return
  const original = node.nodeValue
  const shown = translate(original, lang)
  if (shown === original) { textMemory.delete(node); return }
  textMemory.set(node, { original, shown })
  touchedText.add(new WeakRef(node))
  node.nodeValue = shown
}

function translateAttributes(element, lang) {
  ATTRIBUTES.forEach((name) => {
    if (!element.hasAttribute(name)) return
    const value = element.getAttribute(name)
    const memory = attrMemory.get(element) || {}
    if (memory[name] && memory[name].shown === value) return
    const shown = translate(value, lang)
    if (shown === value) return
    memory[name] = { original: value, shown }
    attrMemory.set(element, memory)
    touchedAttr.add(new WeakRef(element))
    element.setAttribute(name, shown)
  })
}

function walk(root, lang) {
  if (root.nodeType === Node.TEXT_NODE) { translateText(root, lang); return }
  if (root.nodeType !== Node.ELEMENT_NODE) return
  if (root.closest?.('[data-no-translate]')) return
  translateAttributes(root, lang)
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
    acceptNode: (node) => {
      if (node.nodeType === Node.ELEMENT_NODE) {
        const tag = node.tagName
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'TEXTAREA' || node.hasAttribute('data-no-translate')) return NodeFilter.FILTER_REJECT
      }
      return NodeFilter.FILTER_ACCEPT
    },
  })
  let node = walker.nextNode()
  while (node) {
    if (node.nodeType === Node.TEXT_NODE) translateText(node, lang)
    else translateAttributes(node, lang)
    node = walker.nextNode()
  }
}

function restoreAll() {
  touchedText.forEach((ref) => {
    const node = ref.deref()
    const saved = node && textMemory.get(node)
    if (saved && node.nodeValue === saved.shown) node.nodeValue = saved.original
    if (node) textMemory.delete(node)
  })
  touchedAttr.forEach((ref) => {
    const element = ref.deref()
    const memory = element && attrMemory.get(element)
    if (memory) Object.entries(memory).forEach(([name, saved]) => {
      if (element.getAttribute(name) === saved.shown) element.setAttribute(name, saved.original)
    })
    if (element) attrMemory.delete(element)
  })
  touchedText.clear()
  touchedAttr.clear()
}

export function LanguageProvider({ children }) {
  const [lang, setLangState] = useState(readLang)
  const setLang = useCallback((next) => {
    setLangState(next)
    try { localStorage.setItem(STORAGE_KEY, next) } catch { /* trình duyệt chặn lưu trữ */ }
  }, [])

  useEffect(() => {
    document.documentElement.lang = lang
    if (lang !== 'en') { restoreAll(); return undefined }
    walk(document.body, lang)
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'characterData') translateText(mutation.target, lang)
        else if (mutation.type === 'attributes') translateAttributes(mutation.target, lang)
        else mutation.addedNodes.forEach((node) => walk(node, lang))
      })
    })
    observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRIBUTES })
    return () => observer.disconnect()
  }, [lang])

  const t = useCallback((text) => translate(text, lang), [lang])
  const value = useMemo(() => ({ lang, setLang, t }), [lang, setLang, t])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export const useLanguage = () => useContext(I18nContext)

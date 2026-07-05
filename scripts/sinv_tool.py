#!/usr/bin/env python3
"""
SINV Platform Tool — автономный инструмент для работы с крауд-платформой СИНВ.

Возможности:
  - Авторизация через cookie _agiki_session
  - Получение списка идей (постранично)
  - Поиск идей по ключевым словам
  - Скачивание полных текстов идей
  - Сохранение результатов в JSON

Зависимости: requests, beautifulsoup4 (pip install requests beautifulsoup4)
Совместимость: Python 3.9+, Linux/macOS/Windows

ИСПОЛЬЗОВАНИЕ:
  # Сохранить куку сессии
  echo "ВАША_КУКА" > ~/.sinv_session

  # Список идей (страница 1)
  python3 sinv_tool.py list --page 1

  # Поиск идей
  python3 sinv_tool.py search "импортозамещение платформа"

  # Скачать одну идею
  python3 sinv_tool.py download --id 237571

  # Скачать пакет идей (из файла со списком ID)
  python3 sinv_tool.py download --batch ids.txt

  # Скачать все идеи проекта (страницы 1-55)
  python3 sinv_tool.py download --all --pages 1-55

  # Проверить статус сессии
  python3 sinv_tool.py status

НАСТРОЙКИ (переменные окружения):
  SINV_COOKIE_FILE — путь к файлу с кукой (по умолчанию ~/.sinv_session)
  SINV_PROJECT_ID  — ID проекта (по умолчанию 217124)
  SINV_OUTPUT_DIR  — каталог для сохранения (по умолчанию ./sinv_output)

КАК ПОЛУЧИТЬ КУКУ:
  1. Войти на https://идея.росконгресс.рф через браузер
  2. F12 → Application → Cookies → _agiki_session
  3. Скопировать значение
  4. Сохранить: echo "ЗНАЧЕНИЕ" > ~/.sinv_session
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import quote_plus

# ============================================================
# Конфигурация
# ============================================================

BASE_URL = "https://xn--d1ach8g.xn--c1aenmdblfega.xn--p1ai"
DEFAULT_PROJECT_ID = "217124"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
    "Accept-Encoding": "gzip, deflate, br",
}


def _env(key: str, default: str) -> str:
    return os.environ.get(key, default)


# ============================================================
# HTTP-клиент
# ============================================================


class SinvClient:
    """Клиент для работы с API крауд-платформы СИНВ."""

    def __init__(
        self,
        cookie: str,
        project_id: str = DEFAULT_PROJECT_ID,
        verbose: bool = True,
    ):
        import requests

        self._requests = requests
        self.project_id = project_id
        self.project_slug = f"improject-{project_id}"
        self.verbose = verbose
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        self._set_cookies(cookie)
        self._csrf: Optional[str] = None

    # -------- auth --------

    def _set_cookies(self, cookie: str) -> None:
        domain = ".xn--d1ach8g.xn--c1aenmdblfega.xn--p1ai"
        if cookie.startswith("_agiki_session="):
            for part in cookie.split("; "):
                if "=" in part:
                    k, v = part.split("=", 1)
                    self.session.cookies.set(k.strip(), v.strip(), domain=domain)
        else:
            self.session.cookies.set("_agiki_session", cookie, domain=domain)
        self.session.cookies.set("_user", "true", domain=domain)
        self.session.cookies.set("timezone", "-180", domain=domain)

    def _log(self, msg: str) -> None:
        if self.verbose:
            print(f"  {msg}")

    def _ensure_csrf(self) -> str:
        if self._csrf:
            return self._csrf
        r = self.session.get(f"{BASE_URL}/{self.project_slug}")
        r.raise_for_status()
        m = re.search(r'"authenticity_token"\s*:\s*"([^"]+)"', r.text)
        if m:
            self._csrf = m.group(1)
        else:
            self._csrf = ""
        return self._csrf

    # -------- status --------

    def check_status(self) -> Dict[str, Any]:
        """Проверяет валидность сессии."""
        r = self.session.get(
            f"{BASE_URL}/{self.project_slug}/users/316894/pim_expertise",
            allow_redirects=False,
        )
        ok = r.status_code == 200 and "sign_in" not in r.headers.get("Location", "")
        return {
            "authenticated": ok,
            "status_code": r.status_code,
            "url": r.url if ok else (r.headers.get("Location", "")),
            "cookie_len": len(str(self.session.cookies.get("_agiki_session", ""))),
        }

    # -------- list / search --------

    def list_ideas(self, page: int = 1) -> List[Dict[str, Any]]:
        """Возвращает список идей на странице экспертного раздела."""
        url = f"{BASE_URL}/{self.project_slug}"
        if page > 1:
            url += f"?page={page}"
        r = self.session.get(url)
        r.raise_for_status()

        from bs4 import BeautifulSoup

        soup = BeautifulSoup(r.text, "html.parser")
        ideas: List[Dict[str, Any]] = []
        for art in soup.select("#idea-list .article[data-id]"):
            title_el = art.select_one(".article__title")
            author_el = art.select_one(".article__info-username")
            desc_el = art.select_one(".article__text")
            status_el = art.select_one(".expertise-status")
            ideas.append(
                {
                    "id": int(str(art.get("data-id", "0"))),
                    "title": title_el.text.strip()[:120] if title_el else "",
                    "author": author_el.text.strip() if author_el else "",
                    "description": desc_el.text.strip()[:300] if desc_el else "",
                    "status": status_el.text.strip() if status_el else "",
                }
            )
        self._log(f"Стр. {page}: найдено {len(ideas)} идей")
        return ideas

    def search_ideas(self, query: str) -> List[Dict[str, Any]]:
        """Поиск идей по ключевым словам."""
        url = f"{BASE_URL}/search?project={self.project_slug}&scope=pim&q={quote_plus(query)}"
        r = self.session.get(url)
        r.raise_for_status()

        from bs4 import BeautifulSoup

        soup = BeautifulSoup(r.text, "html.parser")
        ideas: List[Dict[str, Any]] = []
        for art in soup.select(".article[data-id]"):
            title_el = art.select_one(".article__title")
            author_el = art.select_one(".article__info-username")
            desc_el = art.select_one(".article__text")
            ideas.append(
                {
                    "id": int(str(art.get("data-id", "0"))),
                    "title": title_el.text.strip()[:120] if title_el else "",
                    "author": author_el.text.strip() if author_el else "",
                    "description": desc_el.text.strip()[:300] if desc_el else "",
                }
            )
        self._log(f"Поиск «{query}»: найдено {len(ideas)} идей")
        return ideas

    # -------- download --------

    def download_idea(self, idea_id: int) -> Optional[Dict[str, Any]]:
        """Скачивает полный текст одной идеи."""
        url = f"{BASE_URL}/{self.project_slug}/ideas/{idea_id}"
        r = self.session.get(url)
        if r.status_code != 200:
            self._log(f"Идея {idea_id}: HTTP {r.status_code}")
            return None

        from bs4 import BeautifulSoup

        soup = BeautifulSoup(r.text, "html.parser")

        # Заголовок
        title_el = soup.select_one(".article__title, h1")
        title = title_el.text.strip() if title_el else ""

        # Основной текст: собираем все блоки article__text и article__subtitle
        sections: List[str] = []
        for block in soup.select(".article__body, .article__text, .article__subtitle"):
            tag_name = block.name
            text = block.get_text(" ", strip=True)
            if text:
                if tag_name in ("h2", "h3", "h4") or "subtitle" in block.get("class", []):
                    sections.append(f"\n## {text}")
                else:
                    sections.append(text)

        full_text = "\n".join(sections) if sections else soup.get_text(" ", strip=True)

        # Метаданные
        author_el = soup.select_one(".article__info-username, .user-name")
        author = author_el.text.strip() if author_el else ""

        section_el = soup.select_one(".article__theme, .tag")
        section = section_el.text.strip() if section_el else ""

        return {
            "id": idea_id,
            "title": title,
            "author": author,
            "section": section,
            "text": full_text[:20000],  # обрезаем на 20K символов
            "url": url,
        }

    def download_batch(
        self,
        idea_ids: List[int],
        output_dir: str,
        delay: float = 0.5,
    ) -> Dict[str, Any]:
        """Скачивает пакет идей и сохраняет в JSON."""
        out = Path(output_dir)
        out.mkdir(parents=True, exist_ok=True)
        results: List[Dict[str, Any]] = []
        errors: List[int] = []

        for i, iid in enumerate(idea_ids):
            self._log(f"[{i + 1}/{len(idea_ids)}] Идея {iid}...")
            data = self.download_idea(iid)
            if data:
                fpath = out / f"idea_{iid}.json"
                fpath.write_text(
                    json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
                )
                results.append({"id": iid, "file": str(fpath), "text_len": len(data["text"])})
            else:
                errors.append(iid)
            if delay > 0 and i < len(idea_ids) - 1:
                time.sleep(delay)

        # Индекс
        index = {str(r["id"]): r for r in results}
        (out / "_index.json").write_text(
            json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8"
        )

        return {"downloaded": len(results), "errors": errors, "dir": str(out)}

    def download_all_pages(
        self,
        output_dir: str,
        page_range: range,
        delay: float = 0.5,
    ) -> Dict[str, Any]:
        """Скачивает все идеи с указанных страниц (сначала листинг, потом тексты)."""
        all_ids: List[int] = []
        for page in page_range:
            ideas = self.list_ideas(page)
            for idea in ideas:
                if idea["id"]:
                    all_ids.append(idea["id"])
            if delay > 0 and page != page_range[-1]:
                time.sleep(delay)

        self._log(f"Всего ID для скачивания: {len(all_ids)}")
        return self.download_batch(all_ids, output_dir, delay)


# ============================================================
# CLI
# ============================================================


def _load_cookie() -> str:
    cookie_file = _env("SINV_COOKIE_FILE", os.path.expanduser("~/.sinv_session"))
    path = Path(cookie_file)
    if not path.exists():
        print(f"Файл с кукой не найден: {cookie_file}")
        print("Создайте его: echo 'ВАША_КУКА' > ~/.sinv_session")
        sys.exit(1)
    return path.read_text().strip()


def _get_client(project_id: Optional[str] = None) -> SinvClient:
    cookie = _load_cookie()
    pid = project_id or _env("SINV_PROJECT_ID", DEFAULT_PROJECT_ID)
    return SinvClient(cookie, pid)


def cmd_status(args: argparse.Namespace) -> None:
    client = _get_client(args.project)
    result = client.check_status()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if result["authenticated"]:
        print("Сессия активна.")
    else:
        print("Сессия НЕАКТИВНА — кука просрочена или невалидна.")


def cmd_list(args: argparse.Namespace) -> None:
    client = _get_client(args.project)
    ideas = client.list_ideas(args.page)
    print(json.dumps(ideas, ensure_ascii=False, indent=2))


def cmd_search(args: argparse.Namespace) -> None:
    client = _get_client(args.project)
    ideas = client.search_ideas(args.query)
    print(json.dumps(ideas, ensure_ascii=False, indent=2))


def cmd_download(args: argparse.Namespace) -> None:
    client = _get_client(args.project)
    output_dir = args.output or _env("SINV_OUTPUT_DIR", "./sinv_output")

    if args.all:
        pages = args.pages or "1-55"
        m = re.match(r"(\d+)-(\d+)", pages)
        if not m:
            print("Формат страниц: N-M (например, 1-55)")
            sys.exit(1)
        page_range = range(int(m.group(1)), int(m.group(2)) + 1)
        result = client.download_all_pages(output_dir, page_range, args.delay)
    elif args.batch:
        batch_path = Path(args.batch)
        if not batch_path.exists():
            print(f"Файл не найден: {args.batch}")
            sys.exit(1)
        ids = [int(line.strip()) for line in batch_path.read_text().splitlines() if line.strip().isdigit()]
        result = client.download_batch(ids, output_dir, args.delay)
    elif args.id:
        result = client.download_batch([args.id], output_dir, args.delay)
    else:
        print("Укажите --id, --batch или --all")
        sys.exit(1)

    print(json.dumps(result, ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="SINV Platform Tool — работа с крауд-платформой СИНВ",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--project", "-p",
        default=None,
        help="ID проекта (по умолчанию 217124 — Национальная предпринимательская инициатива)",
    )

    sub = parser.add_subparsers(dest="command", required=True)

    # status
    sub.add_parser("status", help="Проверить статус сессии")

    # list
    p_list = sub.add_parser("list", help="Список идей")
    p_list.add_argument("--page", type=int, default=1, help="Номер страницы (по 10 идей)")

    # search
    p_search = sub.add_parser("search", help="Поиск идей")
    p_search.add_argument("query", help="Поисковый запрос")

    # download
    p_dl = sub.add_parser("download", help="Скачать идеи")
    p_dl.add_argument("--id", type=int, help="ID одной идеи")
    p_dl.add_argument("--batch", help="Файл со списком ID (по одному на строку)")
    p_dl.add_argument("--all", action="store_true", help="Скачать все идеи проекта")
    p_dl.add_argument("--pages", help="Диапазон страниц для --all (напр. 1-55)")
    p_dl.add_argument("--output", "-o", help="Каталог для сохранения")
    p_dl.add_argument("--delay", type=float, default=0.5, help="Задержка между запросами (сек)")

    args = parser.parse_args()

    commands = {
        "status": cmd_status,
        "list": cmd_list,
        "search": cmd_search,
        "download": cmd_download,
    }
    commands[args.command](args)


if __name__ == "__main__":
    main()

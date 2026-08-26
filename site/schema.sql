-- Схема базы данных сайта НА ЭФИРЕ.
-- Применяется установщиком (install.php); для уже установленного сайта
-- недостающее добирает migrate.php.

CREATE TABLE IF NOT EXISTS settings (
    k VARCHAR(64) NOT NULL PRIMARY KEY,
    v TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Пользователь — это позывной и кодовое слово, больше о нём ничего не известно.
-- Ни почты, ни телефона, ни устройства: восстановить доступ можно только тем же
-- кодовым словом, и это осознанная плата за анонимность.
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- Позывной как его набрал человек, показывается в эфире.
    handle        VARCHAR(32)     NOT NULL,
    -- Он же в нижнем регистре — по нему проверяется занятость имени.
    handle_lower  VARCHAR(32)     NOT NULL,
    -- password_hash от auth_key. Сам auth_key = sha256(позывной:кодовое слово),
    -- посчитанный на телефоне: сервер не видит даже его исходника.
    auth_hash     VARCHAR(255)    NOT NULL,
    -- sha256(auth_key): детерминированный индекс, по которому запрос находит
    -- владельца, не называя позывного. Проверку всё равно делает медленный
    -- auth_hash — этот столбец только для поиска.
    auth_lookup   CHAR(64)        NULL,
    -- Адрес личной ленты. Не выводится из позывного и нигде на сайте
    -- не публикуется: попадает к собеседнику только через эфир.
    profile_code  VARCHAR(24)     NOT NULL,
    posts_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    is_blocked    TINYINT(1)      NOT NULL DEFAULT 0,
    -- Визитка на странице ленты. Заполняется только из приложения и только
    -- по своей воле: по умолчанию все поля пусты и ничего не показывается.
    contact_name     VARCHAR(64)  NOT NULL DEFAULT '',
    contact_phone    VARCHAR(32)  NOT NULL DEFAULT '',
    contact_email    VARCHAR(128) NOT NULL DEFAULT '',
    contact_site     VARCHAR(190) NOT NULL DEFAULT '',
    contact_telegram VARCHAR(64)  NOT NULL DEFAULT '',
    contact_vk       VARCHAR(190) NOT NULL DEFAULT '',
    contact_social   VARCHAR(255) NOT NULL DEFAULT '',
    -- Рубильник: спрятать визитку целиком, не стирая заполненного.
    contacts_public  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME        NOT NULL,
    last_seen_at  DATETIME        NULL,
    UNIQUE KEY uniq_handle (handle_lower),
    UNIQUE KEY uniq_profile (profile_code),
    KEY idx_lookup (auth_lookup)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS posts (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(16)     NOT NULL,
    user_id      BIGINT UNSIGNED NULL,
    nick         VARCHAR(64)     NOT NULL DEFAULT '',
    -- Логический канал, на котором передача ушла в эфир (1..16).
    channel      TINYINT UNSIGNED NOT NULL DEFAULT 1,
    -- То, что реально ушло в радиоэфир (короткая строка из TXT-записи).
    broadcast    VARCHAR(255)    NOT NULL DEFAULT '',
    -- Длинный текст, прикреплённый к сообщению. Файлов нет и не будет:
    -- НА ЭФИРЕ живёт только текст.
    body         MEDIUMTEXT      NOT NULL,
    views        INT UNSIGNED    NOT NULL DEFAULT 0,
    is_hidden    TINYINT(1)      NOT NULL DEFAULT 0,
    ip_hash      CHAR(64)        NOT NULL DEFAULT '',
    created_at   DATETIME        NOT NULL,
    UNIQUE KEY uniq_code (code),
    KEY idx_feed (is_hidden, id),
    KEY idx_user (user_id, id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Журнал публикаций: нужен только для ограничения частоты по IP.
CREATE TABLE IF NOT EXISTS uploads_log (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ip_hash    CHAR(64)        NOT NULL,
    created_at DATETIME        NOT NULL,
    KEY idx_ip_time (ip_hash, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

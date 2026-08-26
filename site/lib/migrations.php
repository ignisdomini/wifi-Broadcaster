<?php
declare(strict_types=1);

/**
 * Миграции схемы.
 *
 * Один модуль на всё: его вызывают и migrate.php, и update.php, чтобы шаги
 * не разъезжались между двумя копиями. Каждый шаг сначала проверяет, не
 * сделан ли он уже, поэтому запускать можно сколько угодно раз.
 */

function has_table(string $table): bool
{
    $row = db_one(
        'SELECT COUNT(*) AS c FROM information_schema.tables
         WHERE table_schema = DATABASE() AND table_name = ?',
        [$table]
    );
    return (int)($row['c'] ?? 0) > 0;
}

function has_column(string $table, string $column): bool
{
    $row = db_one(
        'SELECT COUNT(*) AS c FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?',
        [$table, $column]
    );
    return (int)($row['c'] ?? 0) > 0;
}

/**
 * Приводит базу к текущей схеме.
 *
 * @return array<int, string> человекочитаемый отчёт о том, что сделано
 */
function run_migrations(): array
{
    $log = [];

    if (!has_table('users')) {
        db()->exec(
            "CREATE TABLE users (
                id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                handle        VARCHAR(32)     NOT NULL,
                handle_lower  VARCHAR(32)     NOT NULL,
                auth_hash     VARCHAR(255)    NOT NULL,
                auth_lookup   CHAR(64)        NULL,
                profile_code  VARCHAR(24)     NOT NULL,
                posts_count   INT UNSIGNED    NOT NULL DEFAULT 0,
                is_blocked    TINYINT(1)      NOT NULL DEFAULT 0,
                contact_name     VARCHAR(64)  NOT NULL DEFAULT '',
                contact_phone    VARCHAR(32)  NOT NULL DEFAULT '',
                contact_email    VARCHAR(128) NOT NULL DEFAULT '',
                contact_site     VARCHAR(190) NOT NULL DEFAULT '',
                contact_telegram VARCHAR(64)  NOT NULL DEFAULT '',
                contact_vk       VARCHAR(190) NOT NULL DEFAULT '',
                contact_social   VARCHAR(255) NOT NULL DEFAULT '',
                contacts_public  TINYINT(1)   NOT NULL DEFAULT 1,
                created_at    DATETIME        NOT NULL,
                last_seen_at  DATETIME        NULL,
                UNIQUE KEY uniq_handle (handle_lower),
                UNIQUE KEY uniq_profile (profile_code),
                KEY idx_lookup (auth_lookup)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        );
        $log[] = 'создана таблица позывных';
    }

    if (has_table('users') && !has_column('users', 'auth_lookup')) {
        db()->exec('ALTER TABLE users ADD COLUMN auth_lookup CHAR(64) NULL AFTER auth_hash');
        db()->exec('ALTER TABLE users ADD KEY idx_lookup (auth_lookup)');
        // Задним числом вычислить не из чего: столбец заполнится сам,
        // когда владелец в следующий раз войдёт из приложения.
        $log[] = 'в позывные добавлен индекс ключа';
    }

    // Визитка. Столбцы добавляются пустыми: у тех, кто уже завёл позывной,
    // на странице ленты ничего не изменится, пока они сами не заполнят поля.
    $contactColumns = [
        'contact_name'     => "VARCHAR(64)  NOT NULL DEFAULT ''",
        'contact_phone'    => "VARCHAR(32)  NOT NULL DEFAULT ''",
        'contact_email'    => "VARCHAR(128) NOT NULL DEFAULT ''",
        'contact_site'     => "VARCHAR(190) NOT NULL DEFAULT ''",
        'contact_telegram' => "VARCHAR(64)  NOT NULL DEFAULT ''",
        'contact_vk'       => "VARCHAR(190) NOT NULL DEFAULT ''",
        'contact_social'   => "VARCHAR(255) NOT NULL DEFAULT ''",
        'contacts_public'  => 'TINYINT(1)   NOT NULL DEFAULT 1',
    ];
    $addedContacts = 0;
    foreach ($contactColumns as $column => $definition) {
        if (has_table('users') && !has_column('users', $column)) {
            db()->exec("ALTER TABLE users ADD COLUMN $column $definition");
            $addedContacts++;
        }
    }
    if ($addedContacts > 0) {
        $log[] = 'у позывных появилась визитка';
    }

    if (!has_column('posts', 'user_id')) {
        db()->exec('ALTER TABLE posts ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER code');
        db()->exec('ALTER TABLE posts ADD KEY idx_user (user_id, id)');
        $log[] = 'записи связаны с позывными';
    }

    if (!has_column('posts', 'channel')) {
        db()->exec('ALTER TABLE posts ADD COLUMN channel TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER nick');
        $log[] = 'у записей появился канал';
    }

    if (!has_table('uploads_log')) {
        db()->exec(
            "CREATE TABLE uploads_log (
                id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                ip_hash    CHAR(64)        NOT NULL,
                created_at DATETIME        NOT NULL,
                KEY idx_ip_time (ip_hash, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        );
        $log[] = 'создан журнал публикаций';
    }

    return $log;
}

/**
 * Остатки прежних версий, которые обновление по FTP не удаляет само.
 *
 * Файлы кода убираем молча — это наш код, и работать он больше не должен.
 * Пользовательские данные (таблица images и каталог uploads) не трогаем:
 * решение об их удалении принимает владелец сети, а не скрипт.
 *
 * @return array<int, string>
 */
function cleanup_obsolete_files(): array
{
    $log = [];

    $obsolete = [
        'api/feed.php'            => 'общая лента убрана',
        'assets/scanner.js'       => 'живой сканер убран',
        'partials/feed_item.php'  => 'шаблон общей ленты убран',
        'lib/image.php'           => 'обработка изображений убрана',
    ];

    foreach ($obsolete as $relative => $why) {
        $path = EFIR_ROOT . '/' . $relative;
        if (is_file($path) && @unlink($path)) {
            $log[] = "удалён $relative ($why)";
        }
    }

    // Каталог partials пустеет вместе с шаблоном — убираем и его.
    $partials = EFIR_ROOT . '/partials';
    if (is_dir($partials) && count((array)scandir($partials)) <= 2) {
        if (@rmdir($partials)) {
            $log[] = 'удалён пустой каталог partials';
        }
    }

    return $log;
}

/**
 * То, что скрипт трогать не должен, но о чём обязан предупредить.
 *
 * @return array<int, string>
 */
function leftover_warnings(): array
{
    $warnings = [];

    if (has_table('images')) {
        $row = db_one('SELECT COUNT(*) AS c FROM images');
        $warnings[] = 'Таблица images больше не используется (записей: '
            . (int)($row['c'] ?? 0) . '). Удалить её можно вручную: DROP TABLE images;';
    }

    if (has_column('posts', 'images_count')) {
        $warnings[] = 'Колонка posts.images_count больше не используется. '
            . 'Удалить: ALTER TABLE posts DROP COLUMN images_count;';
    }

    $uploads = EFIR_ROOT . '/uploads';
    if (is_dir($uploads)) {
        $files = array_diff((array)scandir($uploads), ['.', '..']);
        $warnings[] = 'Каталог uploads остался от прежних версий (файлов: '
            . count($files) . '). Файлы сеть больше не принимает — каталог можно удалить.';
    }

    if (is_file(EFIR_ROOT . '/install.php')) {
        $warnings[] = 'На сервере лежит install.php. Удалите его: через него можно '
            . 'переустановить сеть, если кто-то сотрёт config.php.';
    }

    return $warnings;
}

<?php
declare(strict_types=1);

/**
 * Единая точка входа для всех страниц и API.
 * Подключает конфиг, соединение с БД и вспомогательные функции.
 */

define('EFIR_ROOT', dirname(__DIR__));
define('EFIR_VERSION', '1.6');

mb_internal_encoding('UTF-8');
date_default_timezone_set('Europe/Moscow');

require_once __DIR__ . '/helpers.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/settings.php';
require_once __DIR__ . '/content.php';
require_once __DIR__ . '/channels.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/users.php';
require_once __DIR__ . '/contacts.php';
require_once __DIR__ . '/posts.php';

/**
 * Конфиг может отсутствовать — тогда сайт ещё не установлен.
 */
function efir_config(): ?array
{
    static $config = null;
    static $loaded = false;

    if (!$loaded) {
        $loaded = true;
        $path = EFIR_ROOT . '/config.php';
        if (is_file($path)) {
            /** @var array $data */
            $data = require $path;
            $config = is_array($data) ? $data : null;
        }
    }

    return $config;
}

function efir_is_installed(): bool
{
    return efir_config() !== null;
}

/**
 * Требует установленного сайта. Для обычных страниц — редирект на установщик,
 * для API — понятная ошибка в JSON.
 */
function efir_require_installed(bool $isApi = false): void
{
    if (efir_is_installed()) {
        return;
    }
    if ($isApi) {
        json_response(['ok' => false, 'error' => 'Сайт ещё не установлен', 'code' => 'NOT_INSTALLED'], 503);
    }
    header('Location: /install.php');
    exit;
}

<?php
/**
 * Роутер для встроенного сервера PHP (локальная разработка):
 *   php -S 127.0.0.1:8090 -t D:\CLAUDE\efir-site router.php
 *
 * На реальном хостинге эту роль выполняет .htaccess, файл там не нужен.
 */
declare(strict_types=1);

$uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';

// Короткие ссылки /p/КОД
if (preg_match('#^/p/([A-Za-z0-9]{3,16})/?$#', $uri, $m)) {
    $_GET['c'] = $m[1];
    require __DIR__ . '/p.php';
    return true;
}

// Личные ленты /u/КОД
if (preg_match('#^/u/([A-Za-z0-9]{3,24})/?$#', $uri, $m)) {
    $_GET['u'] = $m[1];
    require __DIR__ . '/u.php';
    return true;
}

// Служебные каталоги наружу не отдаём — как и .htaccess на хостинге.
if (preg_match('#^/(lib|partials)/#', $uri) || $uri === '/config.php' || $uri === '/schema.sql') {
    http_response_code(404);
    echo 'Not found';
    return true;
}

// Существующие файлы отдаём как есть.
$path = __DIR__ . str_replace('/', DIRECTORY_SEPARATOR, $uri);
if ($uri !== '/' && is_file($path)) {
    return false;
}

// Несуществующий API — это 404, а не главная страница. Без этого удалённый
// эндпоинт локально «работает», отдавая HTML, и поломка не видна.
if (preg_match('#^/(api|assets)/#', $uri)) {
    http_response_code(404);
    echo 'Not found';
    return true;
}

// Каталоги — их index.php (админка).
if (is_dir($path) && is_file(rtrim($path, '\\/') . DIRECTORY_SEPARATOR . 'index.php')) {
    require rtrim($path, '\\/') . DIRECTORY_SEPARATOR . 'index.php';
    return true;
}

require __DIR__ . '/index.php';
return true;

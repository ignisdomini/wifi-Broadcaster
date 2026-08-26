<?php
declare(strict_types=1);

/**
 * Своя лента для приложения: список записей владельца ключа.
 *
 * Отдельный эндпоинт нужен потому, что читать ленту по коду может кто угодно,
 * а управлять ею — только хозяин, и узнаём мы его по ключу, а не по адресу
 * страницы.
 */

require_once dirname(__DIR__) . '/lib/bootstrap.php';

header('Access-Control-Allow-Origin: *');
efir_require_installed(true);

$user = authenticate_request();
if ($user === null) {
    json_error('Нужен ключ владельца', 'UNAUTHORIZED', 401);
}

$userId = (int)$user['id'];
$limit = max(1, min(100, (int)($_GET['limit'] ?? 50)));
$offset = max(0, (int)($_GET['offset'] ?? 0));

// Скрытые модерацией записи владельцу показываем: он должен понимать,
// почему его сообщение не видно в ленте.
$rows = db_all(
    'SELECT * FROM posts WHERE user_id = ? ORDER BY id DESC LIMIT ' . $limit . ' OFFSET ' . $offset,
    [$userId]
);

$total = db_one('SELECT COUNT(*) AS c FROM posts WHERE user_id = ?', [$userId]);

json_response([
    'ok' => true,
    'total' => (int)($total['c'] ?? 0),
    'profile_url' => profile_url((string)$user['profile_code']),
    'posts' => array_map(static function (array $row): array {
        $view = post_public_view($row);
        $view['hidden'] = (int)$row['is_hidden'] === 1;
        $view['views'] = (int)$row['views'];
        return $view;
    }, $rows),
]);

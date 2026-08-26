<?php
declare(strict_types=1);

/**
 * Веб-установщик ЭФИРа. Создаёт config.php, накатывает схему и заводит пароль
 * администратора. После установки файл стоит удалить — он сам об этом напомнит.
 */

require_once __DIR__ . '/lib/bootstrap.php';

$errors = [];
$done = false;

$form = [
    'db_host'   => $_POST['db_host']   ?? '127.0.0.1',
    'db_port'   => $_POST['db_port']   ?? '3306',
    'db_name'   => $_POST['db_name']   ?? 'efir',
    'db_user'   => $_POST['db_user']   ?? 'root',
    'db_pass'   => $_POST['db_pass']   ?? '',
    'base_url'  => $_POST['base_url']  ?? '',
    'site_name' => $_POST['site_name'] ?? 'РАДИОИНФОРМАТОР',
];

$alreadyInstalled = efir_is_installed();

if ($_SERVER['REQUEST_METHOD'] === 'POST' && !$alreadyInstalled) {
    $password = (string)($_POST['admin_password'] ?? '');
    $password2 = (string)($_POST['admin_password2'] ?? '');

    if (mb_strlen($password) < 8) {
        $errors[] = 'Пароль администратора должен быть не короче 8 символов.';
    }
    if ($password !== $password2) {
        $errors[] = 'Пароли не совпадают.';
    }

    if (!$errors) {
        $dsn = sprintf(
            'mysql:host=%s;port=%d;charset=utf8mb4',
            $form['db_host'],
            (int)$form['db_port']
        );
        try {
            $pdo = new PDO($dsn, $form['db_user'], $form['db_pass'], [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            ]);
            $dbName = preg_replace('/[^A-Za-z0-9_]/', '', $form['db_name']);
            $pdo->exec(
                "CREATE DATABASE IF NOT EXISTS `$dbName` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
            $pdo->exec("USE `$dbName`");

            $schema = file_get_contents(__DIR__ . '/schema.sql');
            if ($schema === false) {
                throw new RuntimeException('Не найден schema.sql');
            }
            // Комментарии вырезаем до разбиения: иначе строка «-- ...» перед
            // первым CREATE TABLE делает комментарием весь оператор целиком.
            $schema = preg_replace('/^\s*--.*$/m', '', $schema) ?? '';
            foreach (array_filter(array_map('trim', explode(';', $schema))) as $statement) {
                $pdo->exec($statement);
            }

            // Каталог для загрузок не нужен: файлы сайт не принимает.
            $config = [
                'db' => [
                    'host'    => $form['db_host'],
                    'port'    => (int)$form['db_port'],
                    'name'    => $dbName,
                    'user'    => $form['db_user'],
                    'pass'    => $form['db_pass'],
                    'charset' => 'utf8mb4',
                ],
                'ip_salt' => bin2hex(random_bytes(16)),
            ];

            $php = "<?php\n// Создано установщиком ЭФИРа " . date('d.m.Y H:i') . "\nreturn " .
                var_export($config, true) . ";\n";
            if (file_put_contents(__DIR__ . '/config.php', $php) === false) {
                throw new RuntimeException('Не удалось записать config.php — проверьте права на каталог');
            }

            // Настройки пишем уже через обычный слой доступа.
            $stmt = $pdo->prepare(
                'INSERT INTO settings (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)'
            );
            $stmt->execute(['admin_password_hash', password_hash($password, PASSWORD_DEFAULT)]);
            $stmt->execute(['site_name', $form['site_name'] !== '' ? $form['site_name'] : 'РАДИОИНФОРМАТОР']);
            $stmt->execute(['base_url', rtrim(trim($form['base_url']), '/')]);

            $done = true;
        } catch (Throwable $e) {
            $errors[] = 'Ошибка установки: ' . $e->getMessage();
        }
    }
}
?>
<!doctype html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Установка ЭФИРа</title>
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body class="page-plain">
<div class="install">
    <span class="brand-mark">РАДИОИНФОРМАТОР</span>
    <p class="muted">Установка сети</p>

    <?php if ($alreadyInstalled): ?>
        <div class="notice notice-ok">
            <p>Сайт уже установлен — <code>config.php</code> на месте.</p>
            <p>Чтобы переустановить, удалите <code>config.php</code> и откройте эту страницу снова.</p>
            <p><a href="/">На главную</a> · <a href="/admin/">В админку</a></p>
        </div>
    <?php elseif ($done): ?>
        <div class="notice notice-ok">
            <p><strong>Готово.</strong> База создана, администратор заведён.</p>
            <p>Теперь удалите файл <code>install.php</code> с сервера.</p>
            <p><a href="/">На главную</a> · <a href="/admin/">В админку</a></p>
        </div>
    <?php else: ?>
        <?php foreach ($errors as $error): ?>
            <div class="notice notice-error"><?= e($error) ?></div>
        <?php endforeach; ?>

        <form method="post" class="form">
            <h2>База данных</h2>
            <label>Хост<input type="text" name="db_host" value="<?= e($form['db_host']) ?>" required></label>
            <label>Порт<input type="number" name="db_port" value="<?= e($form['db_port']) ?>" required></label>
            <label>База<input type="text" name="db_name" value="<?= e($form['db_name']) ?>" required></label>
            <label>Пользователь<input type="text" name="db_user" value="<?= e($form['db_user']) ?>" required></label>
            <label>Пароль<input type="password" name="db_pass" value="<?= e($form['db_pass']) ?>"></label>

            <h2>Сайт</h2>
            <label>Название<input type="text" name="site_name" value="<?= e($form['site_name']) ?>"></label>
            <label>
                Адрес сайта
                <input type="text" name="base_url" value="<?= e($form['base_url']) ?>" placeholder="https://naefire.ru">
                <small>Из него собираются короткие ссылки. Можно оставить пустым — тогда берётся текущий хост.</small>
            </label>

            <h2>Администратор</h2>
            <label>Пароль<input type="password" name="admin_password" required minlength="8"></label>
            <label>Пароль ещё раз<input type="password" name="admin_password2" required minlength="8"></label>

            <button type="submit" class="btn">Установить</button>
        </form>
    <?php endif; ?>
</div>
</body>
</html>

<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/bootstrap.php';
efir_require_installed();
require_once __DIR__ . '/_layout.php';
admin_require();

$saved = false;
$errors = [];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();

    if (isset($_POST['new_password']) && $_POST['new_password'] !== '') {
        $new = (string)$_POST['new_password'];
        if (mb_strlen($new) < 8) {
            $errors[] = 'Новый пароль короче 8 символов.';
        } elseif ($new !== (string)($_POST['new_password2'] ?? '')) {
            $errors[] = 'Новые пароли не совпадают.';
        } else {
            save_settings(['admin_password_hash' => password_hash($new, PASSWORD_DEFAULT)]);
            $saved = true;
        }
    }

    $values = [
        'site_name'           => trim((string)($_POST['site_name'] ?? 'РАДИОИНФОРМАТОР')),
        'tagline'             => trim((string)($_POST['tagline'] ?? '')),
        'base_url'            => rtrim(trim((string)($_POST['base_url'] ?? '')), '/'),

        'max_text_chars'      => clamp_int($_POST['max_text_chars'] ?? 2000, 50, 100000),
        'broadcast_max_bytes' => clamp_int($_POST['broadcast_max_bytes'] ?? 140, 20, 220),
        'rate_limit_per_hour' => clamp_int($_POST['rate_limit_per_hour'] ?? 0, 0, 1000),
        'retention_days'      => clamp_int($_POST['retention_days'] ?? 0, 0, 3650),
        'moderation'          => isset($_POST['moderation']) ? 1 : 0,
        'api_token'           => trim((string)($_POST['api_token'] ?? '')),
    ];

    // Тексты страницы принимаем скопом: их два десятка, и перечислять каждый
    // руками значило бы забыть половину при следующей правке шаблона.
    foreach (content_fields() as $group) {
        foreach ($group['fields'] as $key => $label) {
            if (array_key_exists($key, $_POST)) {
                $values[$key] = trim((string)$_POST[$key]);
            }
        }
    }

    if (!$errors) {
        save_settings($values);
        $saved = true;
    }
}

$s = all_settings(true);
admin_header('Настройки');
?>

<?php if ($saved && !$errors): ?>
    <div class="notice notice-ok">Сохранено. Приложение подхватит новые лимиты при следующем запуске.</div>
<?php endif; ?>
<?php foreach ($errors as $error): ?>
    <div class="notice notice-error"><?= e($error) ?></div>
<?php endforeach; ?>

<form method="post" class="form">
    <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">

    <h2>Сайт</h2>
    <div class="grid-2">
        <label>Название<input type="text" name="site_name" value="<?= e((string)$s['site_name']) ?>"></label>
        <label>Подпись<input type="text" name="tagline" value="<?= e((string)$s['tagline']) ?>"></label>
    </div>
    <label>
        Адрес сайта
        <input type="text" name="base_url" value="<?= e((string)$s['base_url']) ?>" placeholder="https://naefire.ru">
        <small>Из него собираются короткие ссылки. Пусто — берётся текущий хост.</small>
    </label>
    <p class="muted" style="font-size:.86rem">
        Тексты главной страницы правятся на отдельной вкладке —
        <a href="/admin/content.php">содержимое</a>.
    </p>

    <h2>Текст</h2>
    <div class="grid-2">
        <label>
            Максимальная длина текста, символов
            <input type="number" name="max_text_chars" value="<?= (int)$s['max_text_chars'] ?>" min="50" max="100000">
            <small>Длина записи, которую приложение кладёт на сайт. Файлы не принимаются вовсе.</small>
        </label>
        <label>
            Длина радиосообщения, байт
            <input type="number" name="broadcast_max_bytes" value="<?= (int)$s['broadcast_max_bytes'] ?>" min="20" max="220">
            <small>Сколько влезает в саму передачу по воздуху. Выше 200 записи перестают доезжать.</small>
        </label>
    </div>

    <h2>Порядок</h2>
    <div class="grid-2">
        <label>
            Записей с одного адреса в час
            <input type="number" name="rate_limit_per_hour" value="<?= (int)$s['rate_limit_per_hour'] ?>" min="0" max="1000">
            <small>0 — без ограничения, так и стоит по умолчанию.</small>
        </label>
        <label>
            Срок хранения, дней
            <input type="number" name="retention_days" value="<?= (int)$s['retention_days'] ?>" min="0" max="3650">
            <small>0 — хранить бессрочно. Старое удаляется вместе с картинками.</small>
        </label>
        <label>
            Токен для приложения
            <input type="text" name="api_token" value="<?= e((string)$s['api_token']) ?>" placeholder="пусто — загрузка открыта">
            <small>Если задан, приложение должно передавать его в заголовке X-Efir-Token.</small>
        </label>
    </div>
    <label style="flex-direction:row;display:flex;align-items:center;gap:8px">
        <input type="checkbox" name="moderation" value="1" style="width:auto" <?= (int)$s['moderation'] === 1 ? 'checked' : '' ?>>
        Премодерация: новые передачи скрыты, пока их не одобрят
    </label>

    <h2>Пароль администратора</h2>
    <div class="grid-2">
        <label>Новый пароль<input type="password" name="new_password" autocomplete="new-password"></label>
        <label>Ещё раз<input type="password" name="new_password2" autocomplete="new-password"></label>
    </div>

    <div><button class="btn" type="submit">Сохранить</button></div>
</form>

<?php
admin_footer();

function clamp_int(mixed $value, int $min, int $max): int
{
    return max($min, min($max, (int)$value));
}

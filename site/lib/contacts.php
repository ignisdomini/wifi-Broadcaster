<?php
declare(strict_types=1);

/**
 * Визитка на странице ленты.
 *
 * Вся сеть построена на том, что о человеке ничего не известно, и ломать это
 * нельзя. Поэтому визитка — исключение, которое включает сам владелец: пустые
 * поля не показываются вовсе, а заполнить их можно только из приложения, по
 * ключу. Никакой формы «оставьте телефон» на сайте нет и быть не должно.
 *
 * Зачем это вообще: кафе объявляет обед, рынок — привоз, мастер — услугу.
 * Такому человеку нужно, чтобы после эфира с ним можно было связаться, и
 * прятать телефон ему незачем. Тому, кому анонимность дороже, достаточно
 * ничего не заполнять — так по умолчанию и есть.
 */

/**
 * Опись полей визитки: один список для формы, для сохранения и для вывода.
 *
 * @return array<string, array{label:string, hint:string, max:int}>
 */
function contact_fields(): array
{
    return [
        'contact_name' => [
            'label' => 'Имя или название',
            'hint'  => 'Как к вам обращаться: «Кафе на Пушкина», «Мастерская Ольги»',
            'max'   => 64,
        ],
        'contact_phone' => [
            'label' => 'Телефон',
            'hint'  => '+7 900 000-00-00',
            'max'   => 32,
        ],
        'contact_email' => [
            'label' => 'Почта',
            'hint'  => 'name@example.ru',
            'max'   => 128,
        ],
        'contact_site' => [
            'label' => 'Сайт',
            'hint'  => 'example.ru',
            'max'   => 190,
        ],
        'contact_telegram' => [
            'label' => 'Telegram',
            'hint'  => 'позывной без собаки или ссылка t.me/…',
            'max'   => 64,
        ],
        'contact_vk' => [
            'label' => 'VK',
            'hint'  => 'короткое имя или ссылка vk.com/…',
            'max'   => 190,
        ],
        'contact_social' => [
            'label' => 'Другие соцсети',
            'hint'  => 'одна строка, ссылки через запятую',
            'max'   => 255,
        ],
    ];
}

/** Ключи полей визитки. @return array<int, string> */
function contact_field_keys(): array
{
    return array_keys(contact_fields());
}

/**
 * Приводит присланное значение к тому, что уместно хранить.
 *
 * Переводы строк вырезаются: визитка — набор коротких строк, а не текст.
 */
function normalize_contact_value(string $key, string $value): string
{
    $value = trim(preg_replace('/\s+/u', ' ', $value) ?? '');
    if ($value === '') {
        return '';
    }

    if ($key === 'contact_telegram') {
        // Пишут по-разному: @name, t.me/name, https://t.me/name. Храним имя.
        $value = preg_replace('~^(https?://)?(www\.)?t(elegram)?\.me/~iu', '', $value) ?? $value;
        $value = ltrim($value, '@');
    }

    if ($key === 'contact_vk') {
        $value = preg_replace('~^(https?://)?(www\.)?vk\.com/~iu', '', $value) ?? $value;
        $value = ltrim($value, '@');
    }

    if ($key === 'contact_site') {
        $value = preg_replace('~^https?://~iu', '', $value) ?? $value;
        $value = rtrim($value, '/');
    }

    $max = contact_fields()[$key]['max'] ?? 190;
    return mb_substr($value, 0, $max);
}

/**
 * Проверяет заполненное. Пустое поле законно: визитка вся необязательная.
 *
 * @param array<string, string> $values
 * @return array<int, string> список ошибок
 */
function validate_contacts(array $values): array
{
    $errors = [];

    $email = $values['contact_email'] ?? '';
    if ($email !== '' && !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $errors[] = 'Почта выглядит неправильно';
    }

    $phone = $values['contact_phone'] ?? '';
    if ($phone !== '' && !preg_match('/^[0-9+()\-\s]{5,32}$/u', $phone)) {
        $errors[] = 'В телефоне могут быть только цифры, плюс, скобки и дефисы';
    }

    $site = $values['contact_site'] ?? '';
    if ($site !== '' && !preg_match('~^[a-z0-9.-]+\.[a-z]{2,}(/\S*)?$~iu', $site)) {
        $errors[] = 'Адрес сайта выглядит неправильно';
    }

    foreach (['contact_telegram' => 'Telegram', 'contact_vk' => 'VK'] as $key => $title) {
        $handle = $values[$key] ?? '';
        if ($handle !== '' && !preg_match('/^[A-Za-z0-9_.]{2,64}$/', $handle)) {
            $errors[] = "Имя в $title может состоять из латинских букв, цифр, точки и подчёркивания";
        }
    }

    return $errors;
}

/**
 * Визитка пользователя как её видит приложение: голые значения.
 *
 * @param array<string, mixed> $user
 * @return array<string, string>
 */
function user_contacts(array $user): array
{
    $out = [];
    foreach (contact_field_keys() as $key) {
        $out[$key] = (string)($user[$key] ?? '');
    }
    return $out;
}

/** Есть ли что показывать. @param array<string, mixed> $user */
function has_contacts(array $user): bool
{
    if ((int)($user['contacts_public'] ?? 1) !== 1) {
        return false;
    }
    foreach (user_contacts($user) as $value) {
        if ($value !== '') {
            return true;
        }
    }
    return false;
}

/**
 * Готовые строки визитки для вывода на странице ленты.
 *
 * @param array<string, mixed> $user
 * @return array<int, array{label:string, text:string, href:?string}>
 */
function contact_rows(array $user): array
{
    if (!has_contacts($user)) {
        return [];
    }

    $v = user_contacts($user);
    $rows = [];

    if ($v['contact_name'] !== '') {
        $rows[] = ['label' => 'Имя', 'text' => $v['contact_name'], 'href' => null];
    }
    if ($v['contact_phone'] !== '') {
        $digits = preg_replace('/[^0-9+]/', '', $v['contact_phone']) ?? '';
        $rows[] = [
            'label' => 'Телефон',
            'text'  => $v['contact_phone'],
            'href'  => $digits !== '' ? 'tel:' . $digits : null,
        ];
    }
    if ($v['contact_email'] !== '') {
        $rows[] = [
            'label' => 'Почта',
            'text'  => $v['contact_email'],
            'href'  => 'mailto:' . $v['contact_email'],
        ];
    }
    if ($v['contact_site'] !== '') {
        $rows[] = [
            'label' => 'Сайт',
            'text'  => $v['contact_site'],
            'href'  => 'https://' . $v['contact_site'],
        ];
    }
    if ($v['contact_telegram'] !== '') {
        $rows[] = [
            'label' => 'Telegram',
            'text'  => '@' . $v['contact_telegram'],
            'href'  => 'https://t.me/' . $v['contact_telegram'],
        ];
    }
    if ($v['contact_vk'] !== '') {
        $rows[] = [
            'label' => 'VK',
            'text'  => 'vk.com/' . $v['contact_vk'],
            'href'  => 'https://vk.com/' . $v['contact_vk'],
        ];
    }
    if ($v['contact_social'] !== '') {
        $rows[] = ['label' => 'Ещё', 'text' => $v['contact_social'], 'href' => null];
    }

    return $rows;
}

/**
 * Сохраняет визитку владельца ключа.
 *
 * @param array<string, string> $values уже нормализованные значения
 */
function save_contacts(int $userId, array $values, bool $public): void
{
    $sets = [];
    $params = [];
    foreach (contact_field_keys() as $key) {
        $sets[] = "$key = ?";
        $params[] = $values[$key] ?? '';
    }
    $sets[] = 'contacts_public = ?';
    $params[] = $public ? 1 : 0;
    $params[] = $userId;

    db_exec('UPDATE users SET ' . implode(', ', $sets) . ' WHERE id = ?', $params);
}

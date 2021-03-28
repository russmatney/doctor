module.exports = {
  plugins: [
    require('tailwindcss'),
    require('autoprefixer'),

    // TODO test this purge tool
    process.env.NODE_ENV === 'production' &&
    require('@fullhuman/postcss-purgecss')({
      content: [
        './src/ui/**/*.cljs'
      ],
      defaultExtractor: content => content.match(/[A-Za-z0-9-_:/]+/g) || []
    })
  ]
}

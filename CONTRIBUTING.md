# Contributing to Zayit

Thanks for your interest in contributing! A few simple rules keep the project
clean and legally clear.

## License of your contributions (inbound = outbound)

Zayit is licensed under the **GNU AGPL v3** together with the **additional
attribution term** under Section 7(b) of that license (the "Powered by the
technologies that drive Zayit — https://zayitapp.com/" requirement). See
[`LICENSE`](LICENSE) for the full text.

By submitting a contribution (pull request, patch, or any other form), you agree
that your contribution is licensed under **exactly the same terms as the
project**, namely the GNU AGPL v3 **and** the additional attribution term under
Section 7(b).

You **retain the copyright** on your contribution — there is no copyright
assignment and no Contributor License Agreement. You simply license your work
under the project's terms, and the attribution requirement continues to apply to
your contribution and to any work derived from it.

## Developer Certificate of Origin (DCO)

We do not use a CLA. Instead, every commit must be signed off under the
[Developer Certificate of Origin 1.1](https://developercertificate.org/). By
signing off, you certify that you wrote the code or otherwise have the right to
submit it under the project's license.

Add a sign-off line to each commit:

```
Signed-off-by: Your Name <your.email@example.com>
```

The easy way is to use the `-s` flag:

```bash
git commit -s -m "Your message"
```

To sign off the last commit you forgot to sign:

```bash
git commit --amend -s --no-edit
```

A CI check enforces that every commit in a pull request is signed off.

<details>
<summary>Full text of the Developer Certificate of Origin 1.1</summary>

```
Developer Certificate of Origin
Version 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

</details>

## Code style

- Comments in code must be written in English.
- Kotlin code must follow Kotlin conventions and idiomatic style.
- Run the linters before opening a PR:

```bash
./gradlew ktlintCheck detekt
```
